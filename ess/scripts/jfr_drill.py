import sys, collections
sys.argv=[sys.argv[0], sys.argv[1]]
exec(open(sys.argv[0].replace('jfr_drill.py','jfr_analyze.py')).read().split('# 1)')[0])
MARK=('org.apache.maven.project.DefaultProjectBuilder.resolveDependencies','org.apache.maven.model.building.DefaultModelBuilder.build')
for name in ['jdk.ExecutionSample','jdk.SocketRead']:
    ev=events(name); c=collections.Counter(); t=collections.Counter(); n=0
    for e in ev:
        fr=frames(e)
        if not any('ProjectBuilder.buildProjectFromPomFile' in f for f in fr): continue
        n+=1
        k='other'
        for f in fr:
            if f.startswith(MARK[0]): k='DefaultProjectBuilder.resolveDependencies (dependency resolution)'; break
            if f.startswith(MARK[1]): k='DefaultModelBuilder.build (model only)'; break
        c[k]+=1; t[k]+=dur(e)
    print(f"== {name} under buildProjectFromPomFile: {n} of {len(ev)}")
    for k,v in c.most_common(): print(f"   {v:6d}  ({t[k]:.1f}s)  {k}")
# VarHandle hot frame: who calls it
ev=events('jdk.ExecutionSample'); c=collections.Counter()
for e in ev:
    fr=frames(e)
    if fr and 'VarHandleByteArrayAsInts' in fr[0]:
        c[' <- '.join(fr[1:5])]+=1
print("== callers of VarHandleByteArrayAsInts hot frame")
for k,v in c.most_common(3): print(f"   {v:5d} {k}")
