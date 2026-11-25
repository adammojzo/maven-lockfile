# Profilácia – maven-lockfile

**Typ profilácie:** časová, nástroj Java Flight Recorder (OpenJDK 23).

**Scenár:** `maven-lockfile:generate -DincludeMavenPlugins=true` na `pom.xml` projektu (17 build pluginov), 3 behy
na variant, porovnávam mediány.

**Úzke miesto:** `ProjectBuilder.buildFromGav` staval pre každú závislosť celý projekt s
`setResolveDependencies(true)`. Maven tak zbytočne rozlišoval a sťahoval jej tranzitívne závislosti, hoci kód číta iba
parent POM a importované BOM-y. V behu so studenou cache išlo o 1063 čítaní zo siete (68 s čakania).

**Úprava:** `ProjectBuilder` dostal príznak `resolveDependencies`. `LockFileFacade.resolveParentsAndBomsForDependencies`
a `BomResolver` používajú `false`, čiže sa stavia iba model POM. Vygenerovaný lockfile zostal obsahovo rovnaký.

## Výsledky (teplá lokálna cache, medián z 3 behov)

| Metrika | Pred | Po | Zmena |
|---|---|---|---|
| CPU čas – user | 52,5 s | 42,8 s | **−18,6 %** |
| CPU čas – sys | 2,51 s | 2,05 s | −18,3 % |
| Maximálna pamäť (RSS) | 427 MB | 378 MB | **−11,4 %** |
| Celkový čas | 57,1 s | 56,2 s | −1,5 % (v rámci šumu) |

Celkový čas sa nezmenil, lebo ho určuje čakanie na sieť pri sťahovaní artefaktov na výpočet checksumov. Úprava znížila
spotrebu CPU a pamäte.
