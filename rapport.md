## Rapport — gate CI pour score de mutation (étape 1)

Date : 2025-11-08

Résumé
Cette note reprend les actions réalisées pour ajouter une vérification dans la CI qui fait échouer la build si le score de mutation (PIT) diminue après un commit. Le but était d'installer une garde automatique contre les régressions de qualité détectées par PIT et de documenter la démarche / validation.

Objectif

Actions réalisées
1. Ajout d'un script de vérification robuste : `ci/check-mutation-score.sh`
	- Lecture prioritaire du rapport XML (`target/pit-reports/mutations.xml`) pour extraire nombre total / tués.
	- Repli sur `index.html` si le XML est absent (parsing plus fragile mais utile comme fallback).
	- Utilise une petite tolérance (epsilon=0.1) et lit la baseline dans `ci/mutation-baseline.txt`.
	- Renvoie un code de sortie non‑zéro si score < baseline - epsilon.

2. Modification du workflow GitHub Actions (`.github/workflows/build.yml`)
	- Avant d'exécuter PIT pour `core`, on installe les artefacts du reactor afin que PIT puisse résoudre les classes dépendantes :
	  `mvn -B -DskipTests -DskipITs -pl core -am install`
	- Exécution PIT ciblée sur `core/pom.xml` :
	  `mvn -f core/pom.xml -Ppitest org.pitest:pitest-maven:mutationCoverage -Dpitest.outputFormats=XML,HTML -Dpitest.timestampedReports=false`
	- Lancement du parseur/checker : `bash ci/check-mutation-score.sh core`

3. Baseline et politique
	- Fichier baseline : `ci/mutation-baseline.txt` (valeur numérique, pourcentage). Il est prévu d'être mis à jour via PRs justifiées.
	- Pour les tests initiaux une baseline artificiellement haute (50.0) a été utilisée sur la branche de test pour forcer l'échec et vérifier le mécanisme.

4. Validation (local + CI)
	- Local : PIT lancé localement pour `core` (profil `pitest`) et le script de vérification exécuté ; comportement correct (exit non‑zéro quand baseline > actuel).
	- CI : run observé sur la branche `test/mutation-fail` (exemple) :
	  - Run id: 19179954255 — job "Mutation coverage check"
	  - PIT: "Generated 199 mutations Killed 80 (40%)"
	  - Checker : "Current mutation score: 40.0%" puis erreur : "Mutation score decreased: current=40.0% < baseline=50.0% (epsilon=0.1)" => job échouée (comportement attendu)

5. Mise à jour sur `master`
	- Après validation l'équipe a mis à jour la baseline sur `master` à 40.0 pour éviter d'échouer immédiatement les builds existants.

Fichiers modifiés / créés

Commandes clés pour reproduire localement
1) Installer le reactor (dep-build) puis exécuter PIT pour `core` :

```bash
mvn -B -DskipTests -DskipITs -pl core -am install
mvn -f core/pom.xml -Ppitest org.pitest:pitest-maven:mutationCoverage \
  -Dpitest.outputFormats=XML,HTML -Dpitest.timestampedReports=false
```

2) Lancer le checker (depuis la racine du repo) :

```bash
bash ci/check-mutation-score.sh core
```

Résultats de l'exécution PIT locale (2025-11-08)
-----------------------------------------------

Rappel : j'ai exécuté PIT localement avec la commande CI en forçant `-DargLine=""` (voir explication ci‑dessous). Sortie essentielle :

- PIT a généré 204 mutations et en a tué 85 — score brut ≈ 42% (affiché par PIT : "Generated 204 mutations Killed 85 (42%)").
- Rapport XML produit : `core/target/pit-reports/mutations.xml`
- Rapport HTML produit : `core/target/pit-reports/index.html`

Observation sur le script de vérification
----------------------------------------

En exécutant ensuite `bash ci/check-mutation-score.sh core` sur cette machine, le script a retourné une erreur indiquant un score courant égal à `0.0%`. Après investigation rapide, la cause est un détail de parsing :

- Les entrées dans `mutations.xml` utilisent des attributs avec des quotes simples, par exemple `status='KILLED'`.
- Le script courant cherche la chaîne `status="KILLED"` (avec des guillemets doubles) et ne retrouve donc aucun `KILLED` — il calcule `killed=0` et affiche `0.0%`.

Recommandation rapide : modifier `ci/check-mutation-score.sh` pour qu'il accepte indifféremment `status='KILLED'` et `status="KILLED"` (par exemple remplacer la ligne `grep -o "status=\"KILLED\""` par `grep -o "status=[\'\"]KILLED[\'\"]"` ou utiliser an XML parser minimal). Cela permettra au script de fonctionner de manière robuste sur différentes sorties XML.

Choix des classes testées avec Mockito — démarche et justification
-----------------------------------------------------------------

1) Critères de sélection
- Impact : privilégier des classes centrales au module `core` dont le comportement incorrect peut avoir un impact large (par ex. `CHStorage`).
- Facilité de test : préférer des classes où l'on peut isoler la logique métier par injections/mocks (éviter gros I/O ou dépendances réseau lourdes). `Profile` est un bon candidat car il encapsule logique de hints/CustomModel et dépendances simples.
- Rapport PIT / mutations survivantes : inspecter `core/target/pit-reports/index.html` et `mutations.xml` pour repérer des classes avec des mutations survivantes ou des sensations faibles de test.

2) Classes choisies
- `com.graphhopper.storage.CHStorage` : raison — beaucoup de mutations et logique critique liée au stockage des raccourcis ; peut être partiellement testé en simulant `Directory` et `DataAccess`.
- `com.graphhopper.config.Profile` : raison — manipulation de `CustomModel`, hints et règles ; tests simples peuvent couvrir l'API publique et tuer des mutations logiques.

3) Mocks et valeurs simulées
- Pour `CHStorage` : mocks utilisés — `Directory`, deux `DataAccess` (nodesDA, shortcutsDA). Implémentation des mocks : stub `setInt/getInt` en utilisant des HashMap locales pour simuler la mémoire, stub `setHeader`/`flush` comme no-op, vérification des appels `Directory.create(...)`.
- Pour `Profile` : mocks utilisés — `CustomModel`, `TurnCostsConfig`. Tests vérifient que `Profile#setCustomModel` appelle `customModel.internal()` et que `putHint` respecte clés réservées et chaining.

Fichiers créés / modifiés
- `core/src/test/java/com/graphhopper/config/ProfileMockitoTest.java` — nouveaux tests Mockito pour `Profile`.
- `core/src/test/java/com/graphhopper/storage/CHStorageMockitoTest.java` — nouveaux tests Mockito pour `CHStorage` avec deux DataAccess mockés.
- `core/pom.xml` — ajout de la dépendance `mockito-core` (scope test) et ajustement du `argLine` pour permettre l'instrumentation JaCoCo lors des tests locaux.
- `.github/workflows/build.yml` — ajouté triggers PR/dispatch et upload d'artefacts PIT/JaCoCo (poussé sur la branche de test).

Difficultés rencontrées et solutions apportées
----------------------------------------------

1) JaCoCo ne produisait pas `jacoco.exec` localement
- Cause : la configuration Surefire surchargée empêchait JaCoCo `prepare-agent` d'injecter son `-javaagent` dans la JVM de test (l'argLine généré n'était pas conservé). Solution : inclure `${argLine}` dans la configuration `argLine` de Surefire dans `core/pom.xml`.

2) PIT MINION_DIED dû à `${argLine}` littéral
- Symptôme : lors d'un premier run PIT, le minion échouait avec ClassNotFoundException: ${argLine}. Diagnostic : une propriété non résolue `${argLine}` s'est retrouvée dans la ligne de commande passée au minion.
- Solution de contournement locale : exécuter PIT en forçant `-DargLine=""` (commande utilisée ici). Sur CI le problème n'apparaît généralement pas car le plugin Pitest/Surefire gère l'argLine différemment — néanmoins, il faut être conscient de ce piège sur les environnements locaux.

3) Parsing XML par le script de vérification
- Symptôme : `ci/check-mutation-score.sh` a renvoyé 0.0% parce qu'il cherchait `status="KILLED"` (guillemets doubles) alors que `mutations.xml` utilise des quotes simples `status='KILLED'`.
- Solution proposée : rendre le grep tolerant aux deux formes ou utiliser un petit parser XML (xmllint, xmlstarlet) pour une extraction robuste.

4) Tests d'intégration et dépendances lourdes
- Certains tests historiques attendent des fichiers OSM ou interaction disque. Pour garder les nouveaux tests rapides et stables, j'ai préféré moquer les accès bas‑niveau (`DataAccess`, `Directory`) et simuler en mémoire au lieu d'écrire/charger de vrais fichiers.

Validation
----------

- PIT local : run OK après `-DargLine=""`, sortie : "Generated 204 mutations Killed 85 (42%)".
- Script checker : actuellement il tombe à cause du parsing des quotes (0.0%). Après correction du grep pour accepter les quotes simples, le script retournera un code 0 (car 42% >= baseline 40%).

Étapes suivantes recommandées
----------------------------

1. Corriger `ci/check-mutation-score.sh` pour supporter `status='KILLED'` et `status="KILLED"` et re-tester localement.
2. Committer et pousser la documentation `ci/README.md` (je peux le créer si vous le confirmez) en expliquant comment mettre à jour la baseline.
3. Si vous voulez que j'essaie de tuer des mutations survivantes listées dans `mutations.xml`, dites-le; je prioriserai les mutations "SURVIVED" sur `CHStorage`/`Profile` et j'ajouterai tests ciblés.

---


Documentation complète (version intégrée)
=========================================

Objectif
--------
Mettre en place une garde CI qui fait échouer le build lorsqu'une modification réduit le score de mutation (PIT) pour le module `core`. Fournir une documentation claire, simple et reproductible dans ce fichier `rapport.md` (le `ci/README.md` reste optionnel).

Résumé des artefacts ajoutés
---------------------------
- `ci/check-mutation-score.sh` : script Bash qui lit le rapport PIT (XML préféré) et compare le % de mutations tuées à la baseline dans `ci/mutation-baseline.txt`. Retourne 0 si OK, 1 si regression.
- `.github/workflows/build.yml` : job `mutation` qui installe les artefacts nécessaires, lance PIT pour `core` et exécute le script de vérification. Le workflow upload les artefacts PIT et JaCoCo pour diagnostic (artefacts uploadés avec `if: always()`).
- `ci/mutation-baseline.txt` : fichier contenant le pourcentage minimal attendu (ex: `40.0`).
- `core/src/test/java/...` : deux nouveaux tests Mockito (voir section Tests).

Design & choix d'implémentation
-------------------------------
Contrainte principale : ne pas ralentir excessivement le CI et fournir un signal fiable de régression.

Décisions prises
- Exécuter PIT seulement sur le module `core` (où la dette de tests est la plus critique). Cela limite le temps et conserve focus.
- Utiliser la sortie XML `mutations.xml` comme source de vérité (parsing plus robuste qu'un parsing HTML). Garder le fallback HTML pour cas extrêmes.
- Comparer un pourcentage courant au baseline statique contenu dans `ci/mutation-baseline.txt` : simple à vérifier et à mettre à jour via PR.
- Upload des artefacts PIT/JaCoCo (rapports + `jacoco.exec`) avec `if: always()` afin que les auteurs et reviewers puissent télécharger facilement les rapports en cas d'échec.

Script de vérification (`ci/check-mutation-score.sh`) — notes d'implémentation
-----------------------------------------------------------------------
- Lecture prioritaire : `core/target/pit-reports/mutations.xml`.
- Calcul : total = nombre d'éléments `<mutation`; killed = occurrences de `status='KILLED'` ou `status="KILLED"`.
- Comparaison : si current < baseline - EPSILON (EPSILON=0.1) alors exit 1.
- Robustesse : le script a été rendu tolerant aux deux formes de quotes autour de `status` (simple modification grep) pour être portable entre environnements qui produisent des formes légèrement différentes.

Comment valider localement (reproduire CI)
-----------------------------------------
1) Installer les modules nécessaires pour `core` :

```bash
mvn -B -DskipTests -DskipITs -pl core -am install
```

2) Lancer PIT comme en CI :

```bash
mvn -f core/pom.xml -Ppitest org.pitest:pitest-maven:mutationCoverage \
	-Dpitest.outputFormats=XML,HTML -Dpitest.timestampedReports=false
```

3) Exécuter le checker (depuis la racine du repo) :

```bash
bash ci/check-mutation-score.sh core
```

Si le script retourne `exit 1`, cela signifie que la mutation coverage a régressé par rapport à `ci/mutation-baseline.txt`.

Tests ajoutés (Mockito)
------------------------
But : ajouter tests unitaires rapides et ciblés qui ne nécessitent pas de gros I/O et qui tuent des mutations identifiées par PIT.

1) `com.graphhopper.config.ProfileMockitoTest` (fichier : `core/src/test/java/com/graphhopper/config/ProfileMockitoTest.java`)
- Pourquoi : `Profile` gère hints et `CustomModel` — logique simple, facile à isoler.
- Mocks utilisés : `CustomModel`, `TurnCostsConfig` (deux mocks au moins requis).
- Cas testés :
	- `testSetCustomModelCallsInternalAndStoresModel` : vérifie que `setCustomModel` invoque `internal()` sur le `CustomModel` mock, que `getCustomModel` et hints reflètent l'état attendu.
	- `testPutHintRejectsReservedKeysAndChaining` : vérifie comportement `putHint` (chaînage et rejet des clés réservées).

2) `com.graphhopper.storage.CHStorageMockitoTest` (fichier : `core/src/test/java/com/graphhopper/storage/CHStorageMockitoTest.java`)
- Pourquoi : `CHStorage` est central et PIT a montré de nombreuses mutations (tuées et survivantes). On peut tester des chemins essentiels en mockant `Directory` et `DataAccess`.
- Mocks utilisés : `Directory`, deux `DataAccess` (`nodesDA`, `shortcutsDA`) — au moins 2 classes mockées.
- Approche : implémenter `setInt/getInt` des `DataAccess` en stub via `HashMap` locales (simuler mémoire), `setHeader`/`flush` no-op. Vérifier la création de raccourcis, getters (nodeA/nodeB/weight/skippedEdge), et que `flush()` appelle les méthodes attendues.

Pourquoi ces choix
-------------------
- `Profile` : logique contenue et faible dépendance, permet couvrir les interactions publiques (hints/CustomModel) qui tuent des mutations logiques.
- `CHStorage` : grande surface de code critique; beaucoup de mutations trouvées par PIT. Moquer `Directory`/`DataAccess` permet tester des chemins sans manipuler de fichiers OSM.

Difficultés rencontrées et solutions (récapitulatif)
-------------------------------------------------
1) JaCoCo / Surefire argLine
- Problème : certaines configurations Surefire surchargées empêchaient `prepare-agent` JaCoCo d'injecter l'agent (pas de `jacoco.exec`).
- Solution : inclure `${argLine}` dans la configuration `argLine` du plugin Surefire pour préserver l'agent.

2) PIT MINION_DIED localement
- Problème : minion échouait à cause d'une propriété littérale `${argLine}` passée sur certains environnements.
- Solution de contournement local : exécuter PIT en forçant `-DargLine=""`. Sur CI (workflow), le problème n'apparaît normalement pas.

3) Parsing XML du checker (corrigé)
- Problème : script cherchait `status="KILLED"` (double quotes) alors que `mutations.xml` utilisait `status='KILLED'`. Cela faisait que `killed` était compté 0 et le script retournait 0.0%.
- Correction minimale : accepter les deux formes en utilisant `grep -o "status=[\'\"]KILLED[\'\"]"`.

Étapes suivantes recommandées
----------------------------
1. (Optionnel) Remplacer l'extraction par un petit parse XML (`xmllint`/`xmlstarlet`) si vous voulez éliminer toute fragilité liée au formatage. La solution `grep` est suffisante et très simple.
2. Committer la documentation additionnelle dans `rapport.md` (déjà fait ici) et, si souhaité, ajouter un fichier `ci/README.md` plus concis.
3. Prioriser et écrire tests pour les mutations marquées `SURVIVED` dans `core/target/pit-reports/mutations.xml` (je peux aider à générer tests ciblés si vous me le demandez).

Validation (résultat du run local)
---------------------------------
- PIT local (avec `-DargLine=""`) : "Generated 204 mutations Killed 85 (42%)".
- Après correction du script, `bash ci/check-mutation-score.sh core` renvoie le score correctement (parsing tolérant). Si la baseline est `40.0`, le script retournera `exit 0` car 42% >= 40%.

Fermeture
---------
Ce fichier rassemble la documentation demandée (choix de conception, implémentation, validation et commandes pour reproduire). J'ai appliqué la correction minimale au checker et validé localement. Si vous voulez, je peux aussi commiter un petit test supplémentaire ciblant une mutation "SURVIVED" dans `CHStorage`.

