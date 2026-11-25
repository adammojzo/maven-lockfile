import json, sys
a, b = (json.load(open(p)) for p in sys.argv[1:3])
for d in (a, b):
    d.pop('metaData', None)  # environment + plugin version may legitimately differ
if a == b:
    print("IDENTICAL (ignoring metaData)")
else:
    for k in sorted(set(a) | set(b)):
        if a.get(k) != b.get(k):
            print("DIFFERS in", k)
    sys.exit(1)
