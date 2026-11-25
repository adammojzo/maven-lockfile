# DevOps nástroje – maven-lockfile

Projekt: [chains-project/maven-lockfile](https://github.com/chains-project/maven-lockfile), Maven plugin (Java 11),
ktorý generuje a overuje `lockfile.json` s checksumami všetkých závislostí a pluginov.
Stav konfigurácie zodpovedá branchi `ess/tests-coverage-profiling` (vychádza z `main`, commit `d612dce`).

Projekt sa sám zaoberá bezpečnosťou dodávateľského reťazca (supply chain), čo sa odráža aj na jeho DevOps nástrojoch.
Väčšina z nich chráni build pred podvrhnutými závislosťami alebo akciami.

---

## 1. CI/CD – GitHub Actions

Projekt má 13 workflowov v `.github/workflows/`.

| Workflow | Spúšťa sa | Čo robí |
|---|---|---|
| `code-qualitiy.yml` (Maven test) | PR, ručne | `mvn clean verify` (unit aj integračné testy), build, javadoc, `spotless:check`, `pomchecker` (požiadavky Maven Central). Druhý job zostaví projekt 2-krát a porovná SHA-256 JAR súborov (reprodukovateľnosť). |
| `smoke-tests.yml` | PR, ručne | smoke testy cez JBang: plugin sa spustí na 4 cudzích projektoch (Spoon, CoreNLP, JavaParser, Checkstyle) na pevných commitoch |
| `Lockfile.yml` | push | projekt overuje sám seba vlastnou GitHub Action `chains-project/maven-lockfile` |
| `LockfilePR.yml` | PR | to isté pre PR: pri PR z tohto repozitára lockfile po zmene `pom.xml` pregeneruje a commitne, pri PR z forku ho iba overí |
| `codeql.yml` | push, PR, týždenne | CodeQL – statická bezpečnostná analýza Java kódu (SAST) |
| `osv-scanner-pr.yml` | PR, merge queue | OSV-Scanner – známe zraniteľnosti v závislostiach, PR zlyhá pri nájdenej zraniteľnosti, výsledok ide ako SARIF do GitHub Security |
| `osv-scanner-scheduled.yml` | push, týždenne | to isté pravidelne nad celým repozitárom |
| `dependency-review.yml` | PR | Dependency Review – kontrola závislostí pridaných v PR |
| `scorecard.yml` | push, týždenne, zmena branch protection | OpenSSF Scorecard – hodnotenie bezpečnostných praktík repozitára, výsledok sa publikuje |
| `ghasum.yml` | PR | ghasum – aktualizuje súbor s checksumami použitých GitHub Actions (`gha.sum`) |
| `jreleaser.yml` | ručne | release: nová verzia (semver), build, lockfile pre novú verziu, `buildinfo`, podpis a publikovanie |
| `jreleaser-beta.yml` | ručne, každú nedeľu | automatický beta release |
| `ensure-release-notrunning.yml` | PR | zablokuje merge, kým beží release |

## 2. Bezpečnosť dodávateľského reťazca

| Nástroj | Použitie v projekte |
|---|---|
| **step-security/harden-runner** | v každom workflowe. Obmedzuje sieťovú komunikáciu runnera na zoznam povolených domén (`egress-policy: block` v 11 z 13 workflowov, release workflowy iba `audit`). V 8 workflowoch je navyše vypnuté `sudo`. |
| **Pinovanie actions na commit SHA** | všetky použité akcie sú pripnuté na 40-znakový hash commitu, nie na tag |
| **ghasum** | nástroj chains-project. Na začiatku jobov overí checksumy všetkých použitých akcií voči `gha.sum`. |
| **maven-lockfile na sebe samom** | `lockfile.json` v repozitári, overovaný v CI. Plugin je registrovaný aj ako Maven extension v `.mvn/extensions.xml`. |
| **CycloneDX** (`cyclonedx-maven-plugin`) | generuje SBOM (`bom.json`, `bom.xml`), ktorý sa prikladá k releasu |
| **Sigstore** (`sigstore-maven-plugin`) | podpis artefaktov bez kľúčov, lokálne vypnutý cez `sigstore.skip` |
| **Reprodukovateľné buildy** | pevný `project.build.outputTimestamp`, `maven-artifact-plugin` (`buildinfo`) a CI job, ktorý build zopakuje a porovná checksumy |

## 3. Správa závislostí a release

| Nástroj | Použitie v projekte |
|---|---|
| **Renovate** (`renovate.json`) | automatické PR s aktualizáciami a automerge pre major, minor aj patch. `minimumReleaseAge: 1d` chráni pred čerstvo publikovanými (potenciálne škodlivými) verziami. Balíky `org.apache.maven:*` sa neaktualizujú. |
| **JReleaser** (`jreleaser.yml`) | nemenný (immutable) GitHub release, changelog z conventional commits, podpis, publikovanie do Maven Central cez Sonatype Central Portal |
| **Maven Wrapper** (`mvnw`) | rovnaká verzia Mavenu (3.9.6) lokálne aj v CI |

## 4. Kvalita kódu a build

| Nástroj | Použitie v projekte |
|---|---|
| **Spotless** | formátovanie Javy (Palantir Java Format) a zoradenie `pom.xml`, kontroluje sa v CI |
| **maven-enforcer-plugin** | pravidlo `requirePluginVersions`: každý plugin musí mať explicitnú verziu |
| **pomchecker** | POM spĺňa požiadavky Maven Central |
| **Testy** | surefire (unit testy), failsafe s `itf-maven-plugin` (integračné testy v izolovanom Maven repozitári, 6 paralelne, 2 opakovania pri zlyhaní), JBang smoke testy |
| **Develocity (Gradle Enterprise) extension** | build scany na požiadanie (`publish: ON_DEMAND`) |
| **Qodana** | v repozitári je konfigurácia `qodana.yml`, ale žiaden workflow ju nespúšťa |

---

## 5. Možné rozšírenia

Návrhy nie sú implementované.

### Meranie pokrytia kódu v CI

Projekt pokrytie vôbec nemeria: nepoužíva JaCoCo, Codecov ani Coveralls. Lokálne namerané pokrytie riadkov
bolo 36,6 % pred a 55,9 % po doplnení testov, no CI by pokles pokrytia v PR nezachytilo.

- pridať do `code-qualitiy.yml` krok s JaCoCo,
- report nahrať do Codecov alebo ho aspoň zapísať do GitHub job summary a uložiť ako artefakt,
- kvôli harden-runneru doplniť domény Codecovu do `allowed-endpoints` a aktualizovať `gha.sum`.

### Spustenie Qodana

Konfigurácia `qodana.yml` existuje, ale nikto ju nepoužíva. Stačí doplniť workflow s akciou `JetBrains/qodana-action`
(pripnutou na SHA) a výsledky posielať ako SARIF do GitHub Security, rovnako ako pri CodeQL a OSV-Scanneri.

### Pripnutie nástrojov inštalovaných počas behu

Akcie sú pripnuté na SHA, no dva nástroje sa sťahujú bez pevnej verzie:

- `jreleaser.yml`: `go install github.com/ffurrer2/semver/cmd/semver@latest`, a to priamo v release pipeline,
- `smoke-tests.yml`: `curl -Ls https://sh.jbang.dev | bash`.

Návrh: pripnúť konkrétnu verziu (alebo commit) a overiť jej checksum, aby to zodpovedalo prísnosti zvyšku pipeline.

### Oprava cache v smoke testoch

V `smoke-tests.yml` má `actions/cache` kľúč doslova `$-jbang-$` (výraz sa zrejme stratil) a cestu `/root/.jbang`,
hoci JBang sa inštaluje do `~/.jbang` používateľa runnera. Cache tak pravdepodobne nič neukladá. Návrh: kľúč
`${{ runner.os }}-jbang-${{ hashFiles('.github/smoketest/**') }}` a cesta `~/.jbang`.

### Prísnejší harden-runner pri releasoch

Práve release workflowy, ktoré pracujú s tajomstvami na podpis a publikovanie, majú iba `egress-policy: audit`.
Z ich auditných logov sa dá zostaviť zoznam povolených domén a prepnúť ich na `block`.

### Silnejšia kontrola reprodukovateľnosti

Job `reproducibility` robí oba buildy na tom istom runneri tesne po sebe. Rozdiely spôsobené prostredím (iný OS,
iné JDK, časová zóna, locale) tak neodhalí. Návrh: druhý build spustiť v inom jobe s iným prostredím a porovnať
checksumy artefaktov medzi jobmi.

### Testy na viacerých platformách

Testy bežia iba na `ubuntu-latest` s JDK 17. Plugin však počíta checksumy súborov a pracuje s cestami, takže
maticový build (Linux, Windows, macOS a niekoľko verzií JDK) by zachytil chyby závislé od platformy, napríklad pri
koncoch riadkov.
