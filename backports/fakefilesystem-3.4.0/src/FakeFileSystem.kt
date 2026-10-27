/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("-Time")

package okio.fakefilesystem

import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.ArrayIndexOutOfBoundsException
import okio.Buffer
import okio.ByteString
import okio.FileHandle
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.fakefilesystem.FakeFileSystem.Element.Directory
import okio.fakefilesystem.FakeFileSystem.Element.File
import okio.fakefilesystem.FakeFileSystem.Element.Symlink
import okio.fakefilesystem.FakeFileSystem.Operation.READ
import okio.fakefilesystem.FakeFileSystem.Operation.WRITE
import okio.use

/**
 * A fully in-memory file system useful for testing. It includes features to support writing
 * better tests.
 *
 * Use [openPaths] to see which paths have been opened for read or write, but not yet closed. Tests
 * should call [checkNoOpenFiles] in `tearDown()` to confirm that no file streams were leaked.
 *
 * Strict By Default
 * -----------------
 *
 * These actions are not allowed and throw an [IOException] if attempted:
 *
 *  * Moving a file that is currently open for reading or writing.
 *  * Deleting a file that is currently open for reading or writing.
 *  * Moving a file to a path that currently resolves to an empty directory.
 *  * Reading and writing the same file at the same time.
 *  * Opening a file for writing that is already open for writing.
 *
 * Programs that do not attempt any of the above operations should work fine on both UNIX and
 * Windows systems. Relax these constraints individually or call [emulateWindows] or [emulateUnix];
 * to apply the constraints of a particular operating system.
 */
class FakeFileSystem(
  @JvmField
  val clock: Clock = Clock.System,
) : FileSystem() {

  /** File system roots. Each element is a Directory and is created on-demand. */
  private val roots = mutableMapOf<Path, Directory>()

  /**
   * Guards [openFiles], entry admission, deletion, and metadata updates. Everything that admits, registers, retires, or reads an open file holds
   * this lock, so those steps are indivisible even when several threads share one file system.
   * Reading the clock is deliberately kept outside of it: a clock may call back into this file
   * system, and a callback that ran between an admission check and its registration could open a
   * competing handle that the check had already passed.
   */
  private val openFilesLock = SynchronizedObject()

  /** Files that are currently open and need to be closed to avoid resource leaks. */
  private val openFiles = mutableListOf<OpenFile>()

  /**
   * An absolute path with this file system's current working directory. Relative paths will be
   * resolved against this directory when they are used.
   */
  var workingDirectory: Path = "/".toPath()
    set(value) {
      require(value.isAbsolute) {
        "expected an absolute path but was $value"
      }
      field = value
    }

  /**
   * True to allow files to be moved even if they're currently open for read or write. UNIX file
   * systems typically allow open files to be moved; Windows file systems do not.
   */
  var allowMovingOpenFiles = false

  /**
   * True to allow files to be deleted even if they're currently open for read or write. UNIX file
   * systems typically allow open files to be deleted; Windows file systems do not.
   */
  var allowDeletingOpenFiles = false

  /**
   * True to allow the target of an [atomicMove] operation to be an empty directory. Windows file
   * systems typically allow files to replace empty directories; UNIX file systems do not.
   */
  var allowClobberingEmptyDirectories = false

  /**
   * True to permit a file to have multiple [sinks][sink] open at the same time. Both Windows and
   * UNIX file systems permit this but the result may be undefined.
   */
  var allowWritesWhileWriting = false

  /**
   * True to permit a file to have a [source] and [sink] open at the same time. Both Windows and
   * UNIX file systems permit this but the result may be undefined.
   */
  var allowReadsWhileWriting = false

  /**
   * True to allow symlinks to be created. UNIX file systems typically allow symlinks; Windows file
   * systems do not. Setting this to false after creating a symlink does not prevent that symlink
   * from being returned or used.
   */
  var allowSymlinks = false

  /**
   * Canonical paths for every file and directory in this file system. This omits file system roots
   * like `C:\` and `/`.
   */
  @get:JvmName("allPaths")
  val allPaths: Set<Path>
    get() {
      val result = mutableListOf<Path>()
      for (path in roots.keys) {
        result += listRecursively(path)
      }
      result.sort()
      return result.toSet()
    }

  /**
   * Canonical paths currently opened for reading or writing in the order they were opened. This may
   * contain duplicates if a single path is open by multiple readers.
   *
   * Note that this may contain paths not present in [allPaths]. This occurs if a file is deleted
   * while it is still open.
   *
   * The returned list is ordered by the order that the paths were opened.
   */
  @get:JvmName("openPaths")
  val openPaths: List<Path>
    get() = synchronized(openFilesLock) { openFiles.map { it.canonicalPath } }

  /**
   * Confirm that all files that have been opened on this file system (with [source], [sink], and
   * [appendingSink]) have since been closed. Call this in your test's `tearDown()` function to
   * confirm that your program hasn't leaked any open files.
   *
   * Forgetting to close a file on a real file system is a severe error that may lead to a program
   * crash. The operating system enforces a limit on how many files may be open simultaneously. On
   * Linux this is [getrlimit] and is commonly adjusted with the `ulimit` command.
   *
   * [getrlimit]: https://man7.org/linux/man-pages/man2/getrlimit.2.html
   *
   * @throws IllegalStateException if any files are open when this function is called.
   */
  fun checkNoOpenFiles() {
    val snapshot = synchronized(openFilesLock) { openFiles.toList() }
    val firstOpenFile = snapshot.firstOrNull() ?: return
    throw IllegalStateException(
      """
      |expected 0 open files, but found:
      |    ${snapshot.joinToString(separator = "\n    ") { it.canonicalPath.toString() }}
      """.trimMargin(),
      firstOpenFile.backtrace,
    )
  }

  /**
   * Configure this file system to use a Windows-like working directory (`F:\`, unless the working
   * directory is already Windows-like) and to follow a Windows-like policy on what operations
   * are permitted.
   */
  fun emulateWindows() {
    if ("\\" !in workingDirectory.toString()) {
      workingDirectory = "F:\\".toPath()
    }
    allowMovingOpenFiles = false
    allowDeletingOpenFiles = false
    allowClobberingEmptyDirectories = true
    allowWritesWhileWriting = true
    allowReadsWhileWriting = true
  }

  /**
   * Configure this file system to use a UNIX-like working directory (`/`, unless the working
   * directory is already UNIX-like) and to follow a UNIX-like policy on what operations are
   * permitted.
   */
  fun emulateUnix() {
    if ("/" !in workingDirectory.toString()) {
      workingDirectory = "/".toPath()
    }
    allowMovingOpenFiles = true
    allowDeletingOpenFiles = true
    allowClobberingEmptyDirectories = false
    allowWritesWhileWriting = true
    allowReadsWhileWriting = true
    allowSymlinks = true
  }

  override fun canonicalize(path: Path): Path {
    val now = clock.now()
    return synchronized(openFilesLock) {
      val canonicalPath = canonicalizeInternal(path)
      val lookupResult = lookupPath(canonicalPath, now = now)
      if (lookupResult?.element == null) {
        throw FileNotFoundException("no such file: $path")
      }
      lookupResult.path
    }
  }

  /** Don't throw [FileNotFoundException] if the path doesn't identify a file. */
  private fun canonicalizeInternal(path: Path): Path {
    return workingDirectory.resolve(path, normalize = true)
  }

  /**
   * Sets the metadata of type [type] on [path] to [value]. If [value] is null this clears that
   * metadata.
   *
   * Extras are not copied by [copy] but they are moved with [atomicMove].
   *
   * @throws IOException if [path] does not exist.
   */
  @Throws(IOException::class)
  fun <T : Any> setExtra(path: Path, type: KClass<out T>, value: T?) {
    val now = clock.now()
    synchronized(openFilesLock) {
      val canonicalPath = canonicalizeInternal(path)
      val lookupResult = lookupPath(
        canonicalPath = canonicalPath,
        createRootOnDemand = canonicalPath.isRoot,
        resolveLastSymlink = false,
        now = now,
      )
      val element = lookupResult?.element ?: throw FileNotFoundException("no such file: $path")
      if (value == null) {
        element.extras.remove(type)
      } else {
        element.extras[type] = value
      }
    }
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val now = clock.now()
    return synchronized(openFilesLock) {
      val canonicalPath = canonicalizeInternal(path)
      val lookupResult = lookupPath(
        canonicalPath = canonicalPath,
        createRootOnDemand = canonicalPath.isRoot,
        resolveLastSymlink = false,
        now = now,
      )
      lookupResult?.element?.metadata
    }
  }

  override fun list(dir: Path): List<Path> = list(dir, throwOnFailure = true)!!

  override fun listOrNull(dir: Path): List<Path>? = list(dir, throwOnFailure = false)

  private fun list(dir: Path, throwOnFailure: Boolean): List<Path>? {
    val now = clock.now()
    return synchronized(openFilesLock) {
      val canonicalPath = canonicalizeInternal(dir)
      val lookupResult = lookupPath(canonicalPath, now = now)
      if (lookupResult?.element == null) {
        if (throwOnFailure) throw FileNotFoundException("no such directory: $dir") else return null
      }
      val element = lookupResult.element as? Directory
        ?: if (throwOnFailure) throw IOException("not a directory: $dir") else return null

      element.access(now = now)
      element.children.keys.map { dir / it }.sorted()
    }
  }

  override fun source(file: Path): Source {
    val fileHandle = openReadOnly(file)
    return fileHandle.source()
      .also { fileHandle.close() }
  }

  override fun sink(file: Path, mustCreate: Boolean): Sink {
    val fileHandle = open(file, readWrite = true, mustCreate = mustCreate)
    fileHandle.resize(0L) // If the file already has data, get rid of it.
    return fileHandle.sink()
      .also { fileHandle.close() }
  }

  override fun appendingSink(file: Path, mustExist: Boolean): Sink {
    val fileHandle = open(file, readWrite = true, mustExist = mustExist)
    return fileHandle.appendingSink()
      .also { fileHandle.close() }
  }

  override fun openReadOnly(file: Path): FileHandle {
    return open(file, readWrite = false)
  }

  override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
    return open(file, readWrite = true, mustCreate = mustCreate, mustExist = mustExist)
  }

  private fun open(
    file: Path,
    readWrite: Boolean,
    mustCreate: Boolean = false,
    mustExist: Boolean = false,
  ): FileHandle {
    require(!mustCreate || !mustExist) {
      "Cannot require mustCreate and mustExist at the same time."
    }

    // Read user code before admission, then resolve and check the current entry under the lock.
    val now = clock.now()
    return synchronized(openFilesLock) {
      val canonicalPath = canonicalizeInternal(file)
      val lookupResult = lookupPath(canonicalPath, createRootOnDemand = readWrite, now = now)

      if (lookupResult?.element == null && mustExist) {
        throw IOException("$file doesn't exist.")
      }
      if (lookupResult?.element != null && mustCreate) {
        throw IOException("$file already exists.")
      }

      val element: File
      val operation: Operation

      if (readWrite) {
        // Note that this case is used for both write and read/write.
        if (lookupResult?.element is Directory) {
          throw IOException("destination is a directory: $file")
        }
        if (!allowWritesWhileWriting) {
          findOpenFile(canonicalPath, operation = WRITE)?.let {
            throw IOException("file is already open for writing $file", it.backtrace)
          }
        }
        if (!allowReadsWhileWriting) {
          findOpenFile(canonicalPath, operation = READ)?.let {
            throw IOException("file is already open for reading $file", it.backtrace)
          }
        }

        val parent = lookupResult?.parent
          ?: throw FileNotFoundException("parent directory does not exist")
        parent.access(now, true)

        val existing = lookupResult.element
        element = File(createdAt = existing?.createdAt ?: now)
        operation = WRITE

        if (existing is File) {
          synchronized(existing.contentLock) {
            element.data = existing.data.map { it.copy() }.toMutableList()
            element.contentSize = existing.contentSize
            element.resizeErrorHasIndexes = existing.resizeErrorHasIndexes
          }
        }
        parent.children[lookupResult.segment!!] = element
      } else {
        val existing = lookupResult?.element
          ?: throw FileNotFoundException("no such file: $file")
        element = existing as? File ?: throw IOException("not a file: $file")
        operation = READ

        if (!allowReadsWhileWriting) {
          findOpenFile(canonicalPath, operation = WRITE)?.let {
            throw IOException("file is already open for writing $file", it.backtrace)
          }
        }
      }

      element.access(now = now, modified = readWrite)

      val openFile = OpenFile(canonicalPath, operation, Exception("file opened for $operation here"))
      openFiles += openFile

      FakeFileHandle(
        readWrite = readWrite,
        openFile = openFile,
        file = element,
      )
    }
  }

  override fun createDirectory(dir: Path, mustCreate: Boolean) {
    // A clock may re-enter this filesystem. Read it before looking up the entry, and reuse it
    // throughout lookup so no callback can separate the existence check from insertion.
    val now = clock.now()
    synchronized(openFilesLock) {
      val canonicalPath = canonicalizeInternal(dir)
      val lookupResult = lookupPath(canonicalPath, createRootOnDemand = true, now = now)

      val existing = lookupResult?.element
      if (existing != null) {
        if (mustCreate || existing !is Directory) {
          throw IOException("already exists: $dir")
        }
        return
      }

      val parentDirectory = lookupResult.requireParent()
      parentDirectory.children[lookupResult!!.segment!!] = Directory(createdAt = now)
    }
  }

  override fun atomicMove(
    source: Path,
    target: Path,
  ) {
    val now = clock.now()
    synchronized(openFilesLock) {
      val canonicalSource = canonicalizeInternal(source)
      val canonicalTarget = canonicalizeInternal(target)

      // A move replaces the destination entry itself, including a final symlink.
      val targetLookupResult = lookupPath(
        canonicalTarget, createRootOnDemand = true, resolveLastSymlink = false, now = now,
      )
      val sourceLookupResult = lookupPath(canonicalSource, resolveLastSymlink = false, now = now)

      // Universal constraints.
      if (targetLookupResult?.element is Directory) {
        throw IOException("target is a directory: $target")
      }
      val targetParent = targetLookupResult.requireParent()
      if (!allowMovingOpenFiles) {
        findOpenFile(canonicalSource)?.let {
          throw IOException("source is open $source", it.backtrace)
        }
        findOpenFile(canonicalTarget)?.let {
          throw IOException("target is open $target", it.backtrace)
        }
      }
      if (!allowClobberingEmptyDirectories) {
        if (sourceLookupResult?.element is Directory && targetLookupResult?.element is File) {
          throw IOException("source is a directory and target is a file")
        }
      }

      val sourceParent = sourceLookupResult.requireParent()
      val removed = sourceParent.children.remove(sourceLookupResult!!.segment!!)
        ?: throw FileNotFoundException("source doesn't exist: $source")
      targetParent.children[targetLookupResult!!.segment!!] = removed
    }
  }

  override fun delete(path: Path, mustExist: Boolean) {
    val now = clock.now()
    synchronized(openFilesLock) {
      val canonicalPath = canonicalizeInternal(path)

      val lookupResult = lookupPath(
        canonicalPath = canonicalPath,
        createRootOnDemand = true,
        resolveLastSymlink = false,
        now = now,
      )

      if (lookupResult?.element == null) {
        if (mustExist) {
          throw FileNotFoundException("no such file: $path")
        } else {
          return
        }
      }

      if (lookupResult.element is Directory && lookupResult.element.children.isNotEmpty()) {
        throw IOException("non-empty directory")
      }

      if (!allowDeletingOpenFiles) {
        findOpenFile(canonicalPath)?.let {
          throw IOException("file is open $path", it.backtrace)
        }
      }

      val directory = lookupResult.requireParent()
      directory.children.remove(lookupResult.segment!!)
    }
  }

  override fun createSymlink(
    source: Path,
    target: Path,
  ) {
    val now = clock.now()
    synchronized(openFilesLock) {
      val canonicalSource = canonicalizeInternal(source)

      val existingLookupResult = lookupPath(canonicalSource, createRootOnDemand = true, now = now)
      if (existingLookupResult?.element != null) {
        throw IOException("already exists: $source")
      }
      val parent = existingLookupResult.requireParent()

      if (!allowSymlinks) {
        throw IOException("symlinks are not supported")
      }

      parent.children[existingLookupResult!!.segment!!] = Symlink(createdAt = now, target)
    }
  }

  /**
   * Walks the file system looking for [canonicalPath], following symlinks encountered along the
   * way. This function is designed to be used both when looking up existing files and when creating
   * new files into an existing directory.
   *
   * It returns either:
   *
   *  * a path lookup result with an element if that file or directory or symlink exists. This is
   *    useful when reading or writing an existing fie.
   *
   *  * a path lookup result that only got as far as the canonical path's parent, if the parent
   *    exists but the child file does not. This is useful when creating a new file.
   *
   *  * null, if not even the parent directory exists. A file cannot yet be created with this path
   *    because there is no parent to attach it to.
   *
   * This will create the root of the returned path if it does not exist.
   *
   * @param canonicalPath a normalized path, typically the result of [FakeFileSystem.canonicalizeInternal].
   * @param recurseCount used internally to detect cycles.
   * @param resolveLastSymlink true if the result's element must not itself be a symlink. Use this
   *     false for metadata or operations that apply to the path like delete and move. We
   *     always follow symlinks for enclosing directories.
   * @param createRootOnDemand true to create a root directory like `C:\` or `/` if it doesn't
   *     exist. Pass true for mutating operations.
   */
  private fun lookupPath(
    canonicalPath: Path,
    recurseCount: Int = 0,
    resolveLastSymlink: Boolean = true,
    createRootOnDemand: Boolean = false,
    now: Instant,
  ): PathLookupResult? {
    // Callers hold openFilesLock and capture user clock callbacks before entry lookup.
    // 40 is chosen for consistency with the Linux kernel (which previously used 8).
    if (recurseCount > 40) {
      throw IOException("symlink cycle?")
    }

    val rootPath = canonicalPath.root!!
    var root = roots[rootPath]

    // If the path is a root, create it on demand.
    if (root == null) {
      if (!createRootOnDemand) return null
      val createdAt = now
      root = synchronized(openFilesLock) {
        roots[rootPath] ?: Directory(createdAt = createdAt).also { roots[rootPath] = it }
      }
    }

    var parent: Directory? = null
    var lastSegment: ByteString? = null
    var current: Element = root
    var currentPath: Path = rootPath

    var segmentsTraversed = 0
    val segments = canonicalPath.segmentsBytes
    for (segment in segments) {
      lastSegment = segment

      // Push the newest segment.
      if (current !is Directory) {
        throw IOException("not a directory: $currentPath")
      }
      parent = current
      current = current.children[segment] ?: break
      currentPath /= segment
      segmentsTraversed++

      // If it's a symlink, recurse to follow it.
      val isLastSegment = segmentsTraversed == segments.size
      val followSymlinks = !isLastSegment || resolveLastSymlink
      if (current is Symlink && followSymlinks) {
        current.access(now = now)
        // We wanna normalize it in case the target is relative and starts with `..`.
        currentPath = currentPath.parent!!.resolve(current.target, normalize = true)
        val symlinkLookupResult = lookupPath(
          canonicalPath = currentPath,
          recurseCount = recurseCount + 1,
          createRootOnDemand = createRootOnDemand,
          now = now,
        ) ?: return null
        // A missing referent must not leave the symlink paired with its referent's parent.
        if (symlinkLookupResult.element == null) return null
        parent = symlinkLookupResult.parent
        lastSegment = symlinkLookupResult.segment
        current = symlinkLookupResult.element
        currentPath = symlinkLookupResult.path
      }
    }

    return when (segmentsTraversed) {
      segments.size -> {
        PathLookupResult(currentPath, parent, lastSegment, current) // The file.
      }
      segments.size - 1 -> {
        PathLookupResult(currentPath, parent, lastSegment, null) // The enclosing directory.
      }
      else -> null // We found nothing.
    }
  }

  private class PathLookupResult(
    /** The canonical path for the looked up path or its enclosing directory. */
    val path: Path,
    /** Only null if the looked up path is a root. */
    val parent: Directory?,
    /** Only null if the looked up path is a root. */
    val segment: ByteString?,
    /** Non-null if this is a root. Also not null if this file exists. */
    val element: Element?,
  )

  private fun PathLookupResult?.requireParent(): Directory {
    return this?.parent ?: throw IOException("parent directory does not exist")
  }

  private sealed class Element(
    val createdAt: Instant,
  ) {
    var lastModifiedAt: Instant = createdAt
    var lastAccessedAt: Instant = createdAt
    val extras = mutableMapOf<KClass<*>, Any>()

    class File(createdAt: Instant) : Element(createdAt) {
      val contentLock = SynchronizedObject()
      var data = mutableListOf<Buffer>()
      var contentSize = 0L
      var resizeErrorHasIndexes = false

      override val metadata: FileMetadata
        get() = synchronized(contentLock) {
          FileMetadata(
            isRegularFile = true,
            size = contentSize,
            createdAt = createdAt,
            lastModifiedAt = lastModifiedAt,
            lastAccessedAt = lastAccessedAt,
            extras = extras,
          )
        }
    }

    class Directory(createdAt: Instant) : Element(createdAt) {
      /** Keys are path segments. */
      val children = mutableMapOf<ByteString, Element>()

      override val metadata: FileMetadata
        get() = FileMetadata(
          isDirectory = true,
          createdAt = createdAt,
          lastModifiedAt = lastModifiedAt,
          lastAccessedAt = lastAccessedAt,
          extras = extras,
        )
    }

    class Symlink(
      createdAt: Instant,
      /** This may be an absolute or relative path. */
      val target: Path,
    ) : Element(createdAt) {
      override val metadata: FileMetadata
        get() = FileMetadata(
          symlinkTarget = target,
          createdAt = createdAt,
          lastModifiedAt = lastModifiedAt,
          lastAccessedAt = lastAccessedAt,
          extras = extras,
        )
    }

    fun access(
      now: Instant,
      modified: Boolean = false,
    ) {
      lastAccessedAt = now
      if (modified) {
        lastModifiedAt = now
      }
    }

    abstract val metadata: FileMetadata
  }

  private fun findOpenFile(
    canonicalPath: Path,
    operation: Operation? = null,
  ): OpenFile? {
    return synchronized(openFilesLock) {
      openFiles.firstOrNull {
        it.canonicalPath == canonicalPath && (operation == null || operation == it.operation)
      }
    }
  }

  private fun checkOffsetAndCount(
    size: Long,
    offset: Long,
    byteCount: Long,
  ) {
    if (offset or byteCount < 0 || offset > size || size - offset < byteCount) {
      throw ArrayIndexOutOfBoundsException("size=$size offset=$offset byteCount=$byteCount")
    }
  }

  private class OpenFile(
    val canonicalPath: Path,
    val operation: Operation,
    val backtrace: Throwable,
  )

  private enum class Operation {
    READ,
    WRITE,
  }

  private inner class FakeFileHandle(
    readWrite: Boolean,
    private val openFile: OpenFile,
    private val file: File,
  ) : FileHandle(readWrite) {
    private var closed = false

    override fun protectedResize(size: Long) {
      synchronized(file.contentLock) {
        check(!closed) { "closed" }
        require(size >= 0L) {
          if (file.resizeErrorHasIndexes) "endIndex=$size < beginIndex=0" else "endIndex < beginIndex"
        }
        resizeFilePages(file.data, file.contentSize, size)
        if (size > file.contentSize) {
          file.resizeErrorHasIndexes = size >= RESIZE_MESSAGE_THRESHOLD
        } else if (size == 0L) {
          file.resizeErrorHasIndexes = false
        }
        file.contentSize = size
      }

      file.access(now = clock.now(), modified = true)
    }

    override fun protectedSize(): Long = synchronized(file.contentLock) {
      check(!closed) { "closed" }
      file.contentSize
    }

    override fun protectedRead(
      fileOffset: Long,
      array: ByteArray,
      arrayOffset: Int,
      byteCount: Int,
    ): Int = synchronized(file.contentLock) {
      check(!closed) { "closed" }
      checkOffsetAndCount(array.size.toLong(), arrayOffset.toLong(), byteCount.toLong())
      require(fileOffset >= 0L) { "fileOffset=$fileOffset must be non-negative" }

      if (fileOffset >= file.contentSize) return -1
      val toCopy = minOf(file.contentSize - fileOffset, byteCount.toLong()).toInt()
      if (toCopy <= 0) return -1
      var copied = 0
      while (copied < toCopy) {
        val position = fileOffset + copied
        val page = file.data[(position / FILE_PAGE_SIZE).toInt()]
        page.readUnsafe().use { cursor ->
          cursor.seek(position % FILE_PAGE_SIZE)
          val pageCount = minOf(page.size - position % FILE_PAGE_SIZE, (toCopy - copied).toLong()).toInt()
          var pageCopied = 0
          while (pageCopied < pageCount) {
            val count = minOf(cursor.end - cursor.start, pageCount - pageCopied)
            cursor.data!!.copyInto(array, arrayOffset + copied, cursor.start, cursor.start + count)
            copied += count
            pageCopied += count
            if (pageCopied < pageCount) cursor.next()
          }
        }
      }
      toCopy
    }

    override fun protectedWrite(
      fileOffset: Long,
      array: ByteArray,
      arrayOffset: Int,
      byteCount: Int,
    ) {
      synchronized(file.contentLock) {
        check(!closed) { "closed" }
        checkOffsetAndCount(array.size.toLong(), arrayOffset.toLong(), byteCount.toLong())
        require(fileOffset >= 0L) { "fileOffset=$fileOffset must be non-negative" }

        val newSize = maxOf(file.contentSize, fileOffset + byteCount)
        resizeFilePages(file.data, file.contentSize, newSize)
        file.contentSize = newSize
        file.resizeErrorHasIndexes = newSize != 0L
        var copied = 0
        while (copied < byteCount) {
          val position = fileOffset + copied
          val page = file.data[(position / FILE_PAGE_SIZE).toInt()]
          page.readAndWriteUnsafe().use { cursor ->
            cursor.seek(position % FILE_PAGE_SIZE)
            val pageCount = minOf(page.size - position % FILE_PAGE_SIZE, (byteCount - copied).toLong()).toInt()
            var pageCopied = 0
            while (pageCopied < pageCount) {
              val count = minOf(cursor.end - cursor.start, pageCount - pageCopied)
              array.copyInto(cursor.data!!, cursor.start, arrayOffset + copied, arrayOffset + copied + count)
              copied += count
              pageCopied += count
              if (pageCopied < pageCount) cursor.next()
            }
          }
        }
      }
      file.access(now = clock.now(), modified = true)
    }

    override fun protectedFlush() {
      synchronized(file.contentLock) {
        check(!closed) { "closed" }
      }
    }

    override fun protectedClose() {
      synchronized(file.contentLock) {
        if (closed) return
        closed = true
      }
      try {
        file.access(now = clock.now(), modified = readWrite)
      } finally {
        // Always release the registration, including when the clock callback fails.
        synchronized(openFilesLock) { openFiles -= openFile }
      }
    }

    override fun toString() = "FileHandler(${openFile.canonicalPath})"
  }

  override fun toString() = "FakeFileSystem"
}

// Each indexed page bounds cursor traversal independently of the file's total size.
private const val FILE_PAGE_SIZE = 64 * 1024

// Preserve the prior resize error wording after growth, shrink and writes.
private const val RESIZE_MESSAGE_THRESHOLD = 4096L

@Suppress("NOTHING_TO_INLINE")
private inline fun resizeFilePages(pages: MutableList<Buffer>, oldSize: Long, newSize: Long) {
  if (newSize == oldSize) return
  if (newSize < oldSize) {
    val pageCount = if (newSize == 0L) 0 else ((newSize - 1) / FILE_PAGE_SIZE + 1).toInt()
    while (pages.size > pageCount) pages.removeAt(pages.lastIndex)
    if (pages.isNotEmpty()) {
      val tailSize = newSize - (pages.size - 1L) * FILE_PAGE_SIZE
      pages.last().readAndWriteUnsafe().use { cursor ->
        // Unshare the retained tail before changing its limit or later reusing its bytes.
        cursor.seek(tailSize - 1)
        cursor.resizeBuffer(tailSize)
      }
    }
    return
  }

  var position = oldSize
  while (position < newSize) {
    val pageIndex = (position / FILE_PAGE_SIZE).toInt()
    if (pageIndex == pages.size) pages.add(Buffer())
    val pageSize = minOf(FILE_PAGE_SIZE.toLong(), newSize - pageIndex.toLong() * FILE_PAGE_SIZE)
    pages[pageIndex].readAndWriteUnsafe().use { cursor ->
      cursor.resizeBuffer(pageSize)
      do {
        cursor.data!!.fill(0, cursor.start, cursor.end)
      } while (cursor.next() != -1)
    }
    position = pageIndex.toLong() * FILE_PAGE_SIZE + pageSize
  }
}

@JvmName("newFileMetadata")
internal fun FileMetadata(
  isRegularFile: Boolean = false,
  isDirectory: Boolean = false,
  symlinkTarget: Path? = null,
  size: Long? = null,
  createdAt: Instant? = null,
  lastModifiedAt: Instant? = null,
  lastAccessedAt: Instant? = null,
  extras: Map<KClass<*>, Any> = mapOf(),
): FileMetadata {
  return FileMetadata(
    isRegularFile = isRegularFile,
    isDirectory = isDirectory,
    symlinkTarget = symlinkTarget,
    size = size,
    createdAtMillis = createdAt?.toEpochMilliseconds(),
    lastModifiedAtMillis = lastModifiedAt?.toEpochMilliseconds(),
    lastAccessedAtMillis = lastAccessedAt?.toEpochMilliseconds(),
    extras = extras,
  )
}
