from pathlib import Path
import hashlib, subprocess, urllib.request, xml.etree.ElementTree as ET
root=Path('/home/u/lanes/util20261014/workspace/okiofix1')
out=Path('/home/u/lanes/util20261014/out')
version='3.4.0-r576-short-read-2'
base=f'https://kotlin.directory/com/squareup/okio/okio-jvm/{version}/okio-jvm-{version}'
release=out/'okiofix1-release'
for ext in ['pom','jar']:
 with urllib.request.urlopen(base+'.'+ext,timeout=60) as response:
  assert response.status==200
  data=response.read()
  print(ext,'HTTP',response.status,'bytes',len(data),flush=True)
 expected=(release/f'okio-jvm-{version}.{ext}').read_bytes()
 assert data==expected,ext+' differs from locally tested artifact'
 path=out/f'okiofix1-artifacts/published.{ext}'
 path.write_bytes(data)
 print(ext,'SHA-256',hashlib.sha256(data).hexdigest(),flush=True)
jar=out/'okiofix1-artifacts/published.jar'
pom=ET.parse(out/'okiofix1-artifacts/published.pom').getroot()
ns={'p':'http://maven.apache.org/POM/4.0.0'}
assert pom.findtext('p:groupId',namespaces=ns)=='com.squareup.okio'
assert pom.findtext('p:artifactId',namespaces=ns)=='okio-jvm'
assert pom.findtext('p:version',namespaces=ns)==version
print('Published dependencies:',[(d.findtext('p:artifactId',namespaces=ns),d.findtext('p:version',namespaces=ns)) for d in pom.findall('p:dependencies/p:dependency',ns)],flush=True)
bytecode=subprocess.check_output(['javap','-classpath',str(jar),'-c','-p','okio.Okio__OkioKt'],text=True,timeout=60)
(out/'okiofix1-published-use.bytecode.txt').write_text(bytecode)
use=bytecode.split(' R use(')[1]
assert 'Intrinsics.checkNotNull:' not in use
print('Published Okio.use: zero checkNotNull result calls',flush=True)
compiler_cp=(root/'build/nullable-use/test.cp').read_text().strip()
stdlib_cp=subprocess.check_output(['coursier','fetch','-r','https://kotlin.directory','org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0','--classpath'],text=True,timeout=120).strip()
classes=root/'build/nullable-use/published-probe'
classes.mkdir(exist_ok=True)
source=out/'Okiofix1PublishedNullableUseJUnitTest.java'
source.write_text('public final class Okiofix1PublishedNullableUseJUnitTest { @org.junit.Test public void nullablePublicApi() { Okiofix1PublishedNullableUse.main(new String[0]); } }\n')
cp=str(jar)+':'+stdlib_cp+':'+compiler_cp
subprocess.run(['javac','-cp',cp,'-d',str(classes),str(out/'Okiofix1PublishedNullableUse.java'),str(source)],check=True,timeout=60)
report=out/'okiofix1-published-public-api'
subprocess.run(['java','-Xmx128m','-cp',str(classes)+':'+cp,'org.junit.platform.console.ConsoleLauncher','execute','--select-class','Okiofix1PublishedNullableUseJUnitTest','--reports-dir',str(report),'--disable-banner','--details','summary'],check=True,timeout=60)
r=ET.parse(report/'TEST-junit-vintage.xml').getroot()
assert r.attrib['tests']=='1' and r.attrib['failures']=='0' and r.attrib['errors']=='0' and r.attrib['skipped']=='0'
print('Published public Java API XML: 1/1, no result check and exactly one close',flush=True)
