from pathlib import Path
import subprocess
import zipfile
import xml.etree.ElementTree as ET

root=Path('/home/u/lanes/util20261014/workspace/okiofix1')
build=root/'build/nullable-use'
out=Path('/home/u/lanes/util20261014/out')
compiler=(build/'compiler.cp').read_text().strip()
allcp=':'.join((build/n).read_text().strip() for n in ['test.cp','filesystem.cp'])
deps=':'.join(dict.fromkeys(p for p in allcp.split(':') if '/com/squareup/okio/okio-jvm/' not in p and '/com/squareup/okio/okio/' not in p))
source=root/'okio/src/nonWasmTest/kotlin/okio/UseNullableResultTest.kt'
baseline=out/'okiofix1-artifacts/baseline.jar'
fixed=build/'fixed.jar'

def run(args,label,limit=180):
 with (out/f'okiofix1-{label}.log').open('w') as log:
  result=subprocess.run([str(a) for a in args],stdout=log,stderr=subprocess.STDOUT,timeout=limit,cwd=root)
 print(label,'exit',result.returncode,flush=True)
 return result.returncode

def compile_test(jar,label):
 target=build/f'{label}-tests.jar'
 args=['java','-Xmx512m','-cp',compiler,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','1.8','-cp',str(jar)+':'+deps,'-d',target,source]
 if run(args,label+'-compile')!=0: raise RuntimeError(label+' compilation failed')
 return target

def execute_test(jar,tests,label,expected_failures):
 report=out/f'okiofix1-{label}'
 status=run(['java','-Xmx128m','-cp',str(tests)+':'+str(jar)+':'+deps,'org.junit.platform.console.ConsoleLauncher','execute','--select-class','okio.UseNullableResultTest','--reports-dir',report,'--disable-banner','--details','summary'],label,60)
 suites=[ET.parse(p).getroot() for p in report.glob('*.xml')]
 totals={k:sum(int(r.attrib.get(k,0)) for r in suites) for k in ['tests','failures','errors','skipped']}
 print(label,totals,flush=True)
 assert totals=={'tests':7,'failures':expected_failures,'errors':0,'skipped':0},totals
 assert status==(1 if expected_failures else 0)

red=compile_test(baseline,'expanded-red')
execute_test(baseline,red,'expanded-red',4)
green=compile_test(fixed,'green')
for attempt in range(1,4):execute_test(fixed,green,f'green-{attempt}',0)
# Revert only the compiled nullable correction and rebuild the unchanged callers,
# since use and FileSystem.read/write are inline.
with zipfile.ZipFile(fixed) as new,zipfile.ZipFile(baseline) as old,zipfile.ZipFile(build/'mutation.jar','w') as mutation:
 for entry in new.infolist():
  mutation.writestr(entry,old.read(entry.filename))
mutant=build/'mutation.jar'
mutation_test=compile_test(mutant,'mutation')
execute_test(mutant,mutation_test,'mutation',4)

short=build/'short-read'
(short/'ShortReadJUnitTest.java').write_text('public final class ShortReadJUnitTest { @org.junit.Test public void preservesAppends() throws Exception { ShortReadRegression.main(new String[0]); } }\n')
shortcp=str(fixed)+':'+deps+':'+(build/'asm.cp').read_text().strip()
(short/'classes').mkdir(exist_ok=True)
assert run(['javac','-cp',shortcp,'-d',short/'classes',short/'ShortReadRegression.java',short/'ShortReadJUnitTest.java'],'short-read-compile',60)==0
(short/'manifest.mf').write_text('Manifest-Version: 1.0\nPremain-Class: ShortReadRegression\n\n')
assert run(['jar','cfm',short/'ordering-agent.jar',short/'manifest.mf','-C',short/'classes','.'],'short-read-agent',30)==0
for attempt in range(1,4):
 label=f'short-read-{attempt}'
 report=out/f'okiofix1-{label}'
 assert run(['java','-Xmx128m','-javaagent:'+str(short/'ordering-agent.jar'),'-cp',str(short/'classes')+':'+shortcp,'org.junit.platform.console.ConsoleLauncher','execute','--select-class','ShortReadJUnitTest','--reports-dir',report,'--disable-banner','--details','summary'],label,60)==0
 r=ET.parse(report/'TEST-junit-vintage.xml').getroot()
 print(label,r.attrib,flush=True)
 assert r.attrib['tests']=='1' and r.attrib['failures']=='0' and r.attrib['errors']=='0' and r.attrib['skipped']=='0'
