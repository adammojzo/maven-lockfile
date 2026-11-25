# ESS – maven-lockfile: testy, profilovanie a DevOps

Projekt: [chains-project/maven-lockfile](https://github.com/chains-project/maven-lockfile), Maven plugin (Java 11),
ktorý generuje a overuje `lockfile.json` s checksumami všetkých závislostí a pluginov.

Branch s prácou: `ess/tests-coverage-profiling` (vychádza z `main`).

Prostredie merania: Linux 6.8, Intel Core i7-8565U (4 jadrá / 8 vlákien), OpenJDK 23, Maven 3.9.6 (wrapper).

Obsah priečinka `ess/`:

| Cesta | Čo obsahuje |
|---|---|
| `README.md` | tento popis |
| `data/` | namerané hodnoty: pokrytie pred a po, benchmark, výstupy z profilera |
| `scripts/` | skripty, ktorými sa dá meranie zopakovať |
| `jfr/` | surové záznamy z Java Flight Recordera (dajú sa otvoriť v JDK Mission Control) |
| `odovzdanie/` | odovzdávka k testom: `testy-odovzdanie.md`/`.pdf` (testy, pokrytie, test doubles) a screenshoty z JaCoCo |
| `data/jacoco-pred/`, `data/jacoco-po/` | HTML reporty JaCoCo pred a po (otvoriť `index.html`) |

---

## 1. Testy a pokrytie kódu

### 1.1 Meranie pokrytia

Nástroj: **JaCoCo 0.8.13**, spúšťaný z príkazového riadku:

```bash
./mvnw org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test \
       org.jacoco:jacoco-maven-plugin:0.8.13:report
# HTML report: target/site/jacoco/index.html, CSV: target/site/jacoco/jacoco.csv
```

JaCoCo som zámerne **nepridal do `pom.xml`**. Projekt sám seba zamyká vlastným `lockfile.json`, ktorý obsahuje
checksum `pom.xml` aj zoznam build pluginov. Akákoľvek zmena POM by preto vyžadovala pregenerovať lockfile.

Meria sa pokrytie unit testami (surefire). Integračné testy spúšťajú Maven v samostatnom procese, preto ich JaCoCo
agent nezachytí.

### 1.2 Výsledok

| Metrika | Pred | Po | Zmena |
|---|---|---|---|
| Počet unit testov | 74 | **120** | +46 |
| Pokrytie riadkov | 36,6 % (815/2226) | **55,9 %** (1248/2232) | **+19,3 p. b.** |
| Pokrytie vetiev | 30,1 % (327/1088) | **43,3 %** (473/1092) | +13,3 p. b. |
| Pokrytie inštrukcií | 37,5 % | 56,2 % | +18,7 p. b. |
| Pokrytie metód | 48,4 % | 73,6 % | +25,2 p. b. |

Triedy s najväčším nárastom (riadky):

| Trieda | Pred | Po |
|---|---|---|
| `FreezeDependencyMojo` | 0 % | 95,9 % |
| `graph.DependencyGraph` | 0 % | 98,7 % |
| `data.Config` | 55,7 % | 100 % |
| `reporting.LockFileDifference` | 0 % | 100 % |
| `reporting.ValidationPhases` (všetkých 7 fáz) | 0 % | 100 % |
| `resolvers.ProjectBuilder` | 9,4 % | 58,9 % |
| `checksum.AbstractChecksumCalculator` | 22,2 % | 94,4 % |
| `LockFileEquality` | 88,3 % | 100 % |

Úplný rozdiel po triedach je v `data/coverage-diff.txt`.

### 1.3 Nové testy

| Testovacia trieda | Testy | Čo overuje |
|---|---|---|
| `graph/DependencyGraphTest` | 9 | prevod Maven stromu závislostí na model lockfilu: odstránenie koreňa projektu, rodič/potomok, checksumy, prewarm cache volaný raz s unikátnymi artefaktmi, prázdny checksum pre reactor SNAPSHOT, správanie pri konflikte verzií (reduced aj non-reduced režim), deduplikácia |
| `reporting/ValidationPhasesTest` | 12 | všetkých 7 validačných fáz (`isEnabled`, `validate`, `isWarn`), poradie fáz, obsah chybových hlásení |
| `reporting/LockFileDifferenceTest` | 4 | rozdiel dvoch lockfilov (chýbajúce závislosti, pluginy, extensions), defenzívne kópie |
| `FreezeDependencyMojoTest` | 7 | goal `freeze`: pripnutie verzií, `dependencyManagement`, `exactVersionStrings`, pluginy a ich závislosti, varovania, chýbajúci lockfile, **varianty s classifierom (odhalil chybu)** |
| `resolvers/ProjectBuilderTest` | 4 | čo sa posiela do Maven `ProjectBuildingRequest` (vrátane príznaku z optimalizácie v časti 2) |
| `data/ConfigTest` | 4 | predvolená konfigurácia, mapovanie enumov, JSON round-trip |
| `checksum/PomChecksumTest` | 3 | SHA-256 testovací vektor, normalizácia koncov riadkov (CRLF/CR/LF), chýbajúci súbor |
| `.../internal/SpyingDependencyNodeUtilsTest` | 3 | reflexívne čítanie víťaznej verzie z Maven `ConflictData` |

Pomocné triedy pre testy: `checksum/FakeChecksumCalculator`, `graph/DependencyNodes`,
`org/apache/maven/shared/dependency/graph/internal/VerboseDependencyNodes`.

### 1.4 Test, ktorý odhalil chybu

**Chyba:** goal `freeze` identifikoval závislosti iba podľa `groupId:artifactId`. Ak lockfile obsahuje viac variantov
toho istého artefaktu s rôznym **classifierom** (typicky natívne knižnice pre rôzne platformy, napr.
`netty-transport-native-epoll` s `linux-x86_64` a `linux-aarch_64`), do zmrazeného `pom.lockfile.xml` sa dostal
**len jeden** z nich. Ostatné varianty zostali nepripnuté, čo je v rozpore s cieľom celého goalu.

Test `FreezeDependencyMojoTest.classifierVariantsOfTheSameArtifactAreAllManaged` pred opravou zlyhal:

```
Expecting actual:
  ["linux-x86_64"]
to contain exactly in any order:
  ["linux-x86_64", "linux-aarch_64"]
but could not find the following elements:
  ["linux-aarch_64"]
```

**Oprava** (`FreezeDependencyMojo.dependencyKey`): kľúč je `groupId:artifactId` a k nemu sa pridá
`:classifier`, ak ho závislosť má. Pre závislosti bez classifiera sa správanie nemení. Po oprave prechádza
unit test aj všetkých 5 integračných testov pre `freeze`.

### 1.5 Test doubles (fake, stub, mock)

| Druh | Kde | Ako |
|---|---|---|
| **Fake** | `FakeChecksumCalculator` (v `DependencyGraphTest`, `PomChecksumTest`) | Ručne napísaná funkčná náhrada `AbstractChecksumCalculator`. Namiesto čítania súborov a sťahovania z Maven Central vracia deterministický checksum odvodený zo súradníc artefaktu a zaznamenáva volania (napr. koľkokrát a s akými artefaktmi bol volaný `prewarmArtifactCache`). |
| **Stub** | `ValidationPhasesTest`, `FreezeDependencyMojoTest` | Mockito `mock(Config.class)` s `when(...).thenReturn(...)`: každý test nastaví len jednu voľbu, ktorú testovaná fáza číta. `MavenProject` je stub, ktorý vracia iba adresár projektu a cestu k `pom.xml`. |
| **Mock** | `FreezeDependencyMojoTest`, `ProjectBuilderTest` | Overovanie správania: `verify(log).warn(contains("Plugin org.extra:extra-plugin not found in POM"))`, `verify(log, never())...`, `ArgumentCaptor` na zachytenie `ProjectBuildingRequest` poslaného do Maven `ProjectBuilder`, `verify(container, never()).lookup(ArtifactResolver.class)`. |

---

## 2. Profilovanie a optimalizácia výkonu

### 2.1 Scenár a nástroje

- **Scenár:** `mvn io.github.chains-project:maven-lockfile:generate -DincludeMavenPlugins=true` na kópii
  `pom.xml` tohto projektu. Ide o reálny projekt so 17 build pluginmi. Použitý je predvolený režim checksumov
  (`remote`).
- **Profiler:** Java Flight Recorder (súčasť JDK), zapnutý cez
  `MAVEN_OPTS="-XX:StartFlightRecording=filename=gen.jfr,settings=profile"`.
- **Analýza:** `jfr print --json` a vlastný skript `scripts/jfr_analyze.py`. Ten agreguje CPU vzorky
  (`jdk.ExecutionSample`) a blokujúce čítania zo siete (`jdk.SocketRead`) podľa metód pluginu.
  `scripts/jfr_drill.py` rozdelí čas v `ProjectBuilder` na *model building* a *dependency resolution*.
- **Metriky:** `/usr/bin/time` (wall-clock čas, CPU user/sys, max. RSS), medián z 3 behov.

### 2.2 Nájdenie úzkeho hrdla

**Prvá hypotéza bola nesprávna.** Pri čítaní kódu som si všimol, že `FileSystemChecksumCalculator` nemá cache, hoci
strom závislostí pluginov má 1914 uzlov a len 506 unikátnych artefaktov. Profiler ale ukázal, že predvolený režim
je `remote` a táto trieda sa vôbec nepoužíva. Ponaučenie: najprv merať, až potom optimalizovať.

Prvý beh (studená cache, `data/jfr-cold-before.txt`):

- wall-clock **290 s**, no CPU len ~103 s, takže väčšinu času program čakal,
- 3147 blokujúcich čítaní zo siete, spolu 157 s, takmer všetky z `repo.maven.apache.org`,
- **42 % CPU vzoriek** pripadalo na `ProjectBuilder.buildFromGav` volaný z
  `LockFileFacade.resolveParentsAndBomsForDependencies`,
- v rámci neho išlo **1063 čítaní zo siete (68 s blokovania)** a 313 CPU vzoriek do
  `DefaultProjectBuilder.resolveDependencies`.

**Príčina:** pre *každú* závislosť v strome (a znova pre strom každého zo 17 pluginov) plugin postaví celý
`MavenProject` s `setResolveDependencies(true)`. Maven teda rozlíši **a stiahne všetky tranzitívne závislosti tejto
závislosti**, hoci kód potom číta len jej parent POM a importované BOM-y (`hasParent()`, `getParent()`,
`getOriginalModel()`). Výsledok rozlišovania sa zahodí.

### 2.3 Zmena kódu

- `ProjectBuilder` dostal príznak `resolveDependencies`. Pôvodný konštruktor ho ponecháva na `true`, takže
  existujúce správanie sa nemení.
- `LockFileFacade.resolveParentsAndBomsForDependencies` a `BomResolver` používajú `false`. Stavia sa len model POM.
- Cesta pre pluginy a extensions zostala s `true`, lebo `GraphBuildingNodeVisitor` tam používa
  `project.getArtifacts()` na doriešenie verzií `RELEASE`/`LATEST`.

**Overenie správnosti:** lockfile vygenerovaný pôvodnou a novou verziou je **obsahovo identický**
(`scripts/same_lockfile.py`, porovnáva sa všetko okrem `metaData`). `./mvnw verify` prechádza: 120/120 unit testov
a 49/49 integračných testov.

### 2.4 Namerané hodnoty pred a po

Teplá lokálna cache (`~/.m2` už obsahuje všetko), 3 behy, medián (`data/benchmark-generate.csv`):

| Metrika | Pred | Po | Zmena |
|---|---|---|---|
| CPU čas – user | 52,5 s | **42,8 s** | **−18,6 %** |
| CPU čas – sys | 2,51 s | 2,05 s | −18,3 % |
| CPU čas spolu | 55,0 s | **44,8 s** | **−18,5 %** |
| Max. pamäť (RSS) | 427 MB | **378 MB** | **−11,4 %** |
| Wall-clock čas | 57,1 s | 56,2 s | −1,5 % (v rámci šumu) |

Profiler s teplou cache (`data/jfr-warm-before.txt`, `data/jfr-warm-after.txt`): CPU vzorky v
`DefaultProjectBuilder.resolveDependencies` klesli z **37 na 8**. Zvyšných 8 pochádza z cesty pluginov, kde
rozlišovanie zostalo zámerne.

**Energetická náročnosť:** priame meranie energie (Intel RAPL `/sys/class/powercap/.../energy_uj`, `perf stat -e
power/energy-pkg/`) vyžaduje root (`perf_event_paranoid=4`). Ako náhradnú metriku som použil spotrebovaný CPU čas,
ktorý klesol o 18,5 %. S `sudo` by sa dalo merať napr.
`sudo perf stat -e power/energy-pkg/ ./mvnw ... generate`.

**Prečo sa neskrátil wall-clock čas:** pri teplej cache celkový čas určuje `RemoteChecksumCalculator`. Ten
paralelne sťahuje artefakty z Maven Central, aby ich zahashoval (~30 % CPU vzoriek, čakanie na sieť). Ušetrený CPU
čas sa preto neprejaví v celkovom čase, ale znižuje záťaž a spotrebu. Pri studenej cache (napr. čerstvý CI runner)
odpadá aj sťahovanie zbytočných JAR-ov. V pôvodnom behu na to pripadalo 68 s čakania na sieť. Kontrolované meranie
so studenou cache pred a po som však nerobil.

**Ďalšie možné zlepšenie:** `buildFromGav` sa pre tú istú závislosť volá opakovane pre každý plugin. Cache
postavených modelov podľa GAV by odstránila zvyšný opakovaný *model building* (~33 % CPU vzoriek po zmene).

---

## 3. DevOps nástroje a služby projektu

### 3.1 CI/CD – GitHub Actions (13 workflowov)

| Workflow | Spúšťač | Účel |
|---|---|---|
| `code-qualitiy.yml` (Maven test) | PR, ručne | `mvn verify` (unit aj integračné testy), build, kontrola javadocu, `spotless:check`, `pomchecker` (požiadavky Maven Central). Druhý job **reproducibility**: build 2× a porovnanie SHA-256 JAR-ov. |
| `smoke-tests.yml` | PR, ručne | smoke testy cez JBang (`.github/smoketest/SmokeTest.java`) proti iným reálnym repozitárom |
| `Lockfile.yml` | push na `main` | *dogfooding*: projekt sám seba overuje vlastným pluginom (GitHub Action `chains-project/maven-lockfile`) |
| `LockfilePR.yml` | PR | to isté na PR: pri PR z toho istého repozitára po zmene `pom.xml` lockfile pregeneruje a commitne, pri PR z forku ho len overí |
| `codeql.yml` | push, PR, týždenne | **CodeQL** – statická bezpečnostná analýza (SAST) |
| `osv-scanner-pr.yml`, `osv-scanner-scheduled.yml` | PR, merge queue, týždenne | **OSV-Scanner** (Google) – známe zraniteľnosti v závislostiach, výsledky ako SARIF do GitHub Security |
| `dependency-review.yml` | PR | **Dependency Review** – blokuje PR, ktoré pridávajú zraniteľné závislosti |
| `scorecard.yml` | push, týždenne, zmena branch protection | **OpenSSF Scorecard** – hodnotenie bezpečnostných praktík repozitára |
| `ghasum.yml` | PR | **ghasum** – overuje/aktualizuje checksumy použitých GitHub Actions (`gha.sum`) |
| `jreleaser.yml` | ručne | release: verzia (semver), build, podpis, lockfile, `buildinfo`, publikovanie |
| `jreleaser-beta.yml` | ručne, každú nedeľu | automatický beta release |
| `ensure-release-notrunning.yml` | PR | zablokuje merge, kým beží release |

### 3.2 Bezpečnosť dodávateľského reťazca (supply chain)

- **step-security/harden-runner** v každom jobe: `egress-policy: block` so zoznamom povolených domén a vypnutým
  `sudo`. Runner sa nedostane na iné adresy, než ktoré sú vymenované.
- **Pinovanie actions na commit SHA** plus **ghasum** (vlastný nástroj chains-project), ktorý overuje checksum
  každej použitej akcie.
- **CycloneDX** (`cyclonedx-maven-plugin`) – generuje SBOM (`bom.json`, `bom.xml`) vo fáze `package`. SBOM sa
  prikladá k releasu.
- **Sigstore** (`sigstore-maven-plugin`) – keyless podpis artefaktov pri releasi (lokálne vypnuté cez
  `sigstore.skip`).
- **Reprodukovateľné buildy** – `project.build.outputTimestamp`, `maven-artifact-plugin` (`buildinfo`) a CI job,
  ktorý build zopakuje a porovná checksumy.
- **maven-lockfile** na sebe samom – `lockfile.json` v repozitári. Plugin je zaregistrovaný aj ako Maven core
  extension v `.mvn/extensions.xml` (hermetic režim zachytáva dynamicky rozlišované artefakty).

### 3.3 Správa závislostí a release

- **Renovate** (`renovate.json`) – automatické PR s aktualizáciami a automerge pre major/minor/patch.
  Pinuje digesty GitHub Actions, `minimumReleaseAge: 1d` chráni pred čerstvo publikovanými (potenciálne
  škodlivými) verziami. `org.apache.maven:*` sa neaktualizuje.
- **JReleaser** (`jreleaser.yml`) – GitHub release (immutable), changelog z conventional commits, podpis,
  publikovanie do **Maven Central** cez Sonatype Central Portal.
- **Maven Wrapper** (`mvnw`) – rovnaká verzia Mavenu všade.

### 3.4 Kvalita kódu a build

- **Spotless** – formátovanie Javy (Palantir Java Format) a zoradenie `pom.xml`, kontroluje sa v CI.
- **maven-enforcer-plugin** – vyžaduje explicitné verzie pluginov (zakázané `LATEST`/`RELEASE`/SNAPSHOT).
- **pomchecker** – POM spĺňa požiadavky Maven Central.
- **Qodana** (JetBrains) – v repozitári je konfigurácia `qodana.yml`, ale žiaden workflow ju nespúšťa.
- **Develocity / Gradle Enterprise Maven extension** (`.mvn/extensions.xml`, `.mvn/gradle-enterprise.xml`) – build
  scany na požiadanie (`publish: ON_DEMAND`).
- **Testy:** surefire (unit), failsafe + `itf-maven-plugin` (integračné testy v izolovanom Maven repozitári, 6
  paralelne, 2 opakovania pri zlyhaní), JBang smoke testy.

### 3.5 Čo chýba – návrh integrácie

Projekt **nemeria pokrytie kódu v CI**: žiaden JaCoCo, Codecov ani Coveralls. Návrh:

1. pridať do jobu `code-qualitiy.yml` krok s JaCoCo (rovnaký príkaz ako v 1.1),
2. nahrať report do **Codecov** (`codecov/codecov-action`) alebo ho aspoň vypísať do GitHub job summary
   a uložiť ako artefakt,
3. kvôli harden-runneru doplniť do `allowed-endpoints` domény Codecovu a aktualizovať `gha.sum` (ghasum).

---

## 4. Ako to zopakovať

```bash
# testy a pokrytie
./mvnw org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report
python3 ess/scripts/covdiff.py ess/data/coverage-before.csv target/site/jacoco/jacoco.csv

# integračné testy
./mvnw verify

# benchmark (plugin musí byť nainštalovaný v ~/.m2)
./mvnw install -DskipTests
ess/scripts/bench.sh after 3

# profilovanie
MAVEN_OPTS="-XX:StartFlightRecording=filename=gen.jfr,settings=profile" \
  ./mvnw -f <projekt>/pom.xml io.github.chains-project:maven-lockfile:5.18.4-SNAPSHOT:generate -DincludeMavenPlugins=true
python3 ess/scripts/jfr_analyze.py gen.jfr
python3 ess/scripts/jfr_drill.py gen.jfr
```

## 5. Zmenené súbory

Produkčný kód:

- `FreezeDependencyMojo.java` – oprava chyby s classifiermi (`dependencyKey`)
- `resolvers/ProjectBuilder.java` – príznak `resolveDependencies`
- `LockFileFacade.java`, `resolvers/BomResolver.java` – model-only build pre parent/BOM

Testy (nové): `DependencyGraphTest`, `ValidationPhasesTest`, `LockFileDifferenceTest`, `FreezeDependencyMojoTest`,
`ProjectBuilderTest`, `ConfigTest`, `PomChecksumTest`, `SpyingDependencyNodeUtilsTest` a pomocné triedy
`FakeChecksumCalculator`, `DependencyNodes`, `VerboseDependencyNodes`.
