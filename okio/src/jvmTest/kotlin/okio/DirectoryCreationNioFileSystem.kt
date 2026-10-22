package okio

import java.nio.file.FileSystem as NioFileSystem

/** Delegates all storage to a real NIO filesystem and orders path resolution at its public boundary. */
internal class DirectoryCreationNioFileSystem(
  private val delegate: NioFileSystem,
  private val beforeResolve: (String) -> Unit,
) : NioFileSystem() {
  override fun provider() = delegate.provider()
  override fun close() = delegate.close()
  override fun isOpen() = delegate.isOpen
  override fun isReadOnly() = delegate.isReadOnly
  override fun getSeparator() = delegate.separator
  override fun getRootDirectories() = delegate.rootDirectories
  override fun getFileStores() = delegate.fileStores
  override fun supportedFileAttributeViews() = delegate.supportedFileAttributeViews()
  override fun getPath(first: String, vararg more: String): java.nio.file.Path {
    beforeResolve(first)
    return delegate.getPath(first, *more)
  }
  override fun getPathMatcher(syntaxAndPattern: String) = delegate.getPathMatcher(syntaxAndPattern)
  override fun getUserPrincipalLookupService() = delegate.userPrincipalLookupService
  override fun newWatchService() = delegate.newWatchService()
}
