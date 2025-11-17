## Rapport

Date : 2025-11-08

Résumé
Cette note reprend les actions réalisées pour ajouter une vérification dans la CI qui fait échouer la build si le score de mutation (PIT) diminue après un commit. Le but était d'installer une garde automatique contre les régressions de qualité détectées par PIT et de documenter la démarche / validation.

# 1 - Modifier le workflow GitHub Actions de GraphHopper de sorte que le processus de build échoue si le score de mutation diminue après un commit

Actions réalisées

1) Script de vérification — [ci/check-mutation-score.sh](https://github.com/Aissatou26/graphhopper/blob/master/ci/check-mutation-score.sh)

- Rôle : lit le rapport PIT XML généré par PIT (par défaut `core/target/pit-reports/mutations.xml`), calcule le pourcentage de mutations tuées, le compare à la baseline définie dans [ci/mutation-baseline.txt](https://github.com/Aissatou26/graphhopper/blob/master/ci/mutation-baseline.txt) et renvoie un code de sortie non‑zéro si le score courant < baseline - EPSILON (EPSILON ≈ 0.1).

- Détails techniques : recherche les occurrences `status='KILLED'` ou `status="KILLED"` dans le XML (fallback possible vers le HTML `core/target/pit-reports/index.html` si le XML manque). 

- Usage :
## depuis la racine du dépôt
bash ci/check-mutation-score.sh core


2) Intégration CI — [.github/workflows/build.yml](https://github.com/Aissatou26/graphhopper/blob/master/.github/workflows/build.yml#L45
)

- Rôle : exécute la build/test, lance PIT pour le module `core` puis appelle le script de vérification. Si le script retourne un code non‑zéro, le job CI échoue, provoquant l'échec de la build.
- Étapes importantes :

	- Installer les artefacts du reactor pour que PIT résolve correctement les dépendances :

	mvn -B -DskipTests -DskipITs -pl core -am install

	- Lancer PIT sur `core` (génère XML + HTML) :

	mvn -f core/pom.xml -Ppitest org.pitest:pitest-maven:mutationCoverage \
		-Dpitest.outputFormats=XML,HTML -Dpitest.timestampedReports=false

	- Appeler le checker pour comparer au baseline :

	bash ci/check-mutation-score.sh core


	- Remarque : le workflow uploade les rapports PIT/JaCoCo avec `if: always()` pour faciliter le diagnostic en cas d'échec.


3. Baseline et politique
	- Fichier baseline : [ci/mutation-baseline.txt](ci/mutation-baseline.txt) (valeur numérique, pourcentage). Il est prévu d'être mis à jour via PRs justifiées.
	- Pour les tests initiaux une baseline artificiellement haute (`50.0`) a été utilisée sur la branche de test pour forcer l'échec et vérifier le mécanisme.

4. Validation (local + CI)
	- Local : PIT lancé localement pour `core` (profil `pitest`) et le script de vérification exécuté (`ci/check-mutation-score.sh`) ; comportement correct (exit non‑zéro quand baseline > actuel).
	- CI : run observé sur la branche `test/mutation-fail` (exemple) :
	  - Run id: 19179954255 — job "Mutation coverage check"
	  - PIT: "Generated 199 mutations Killed 80 (40%)"
	  - Checker : "Current mutation score: 40.0%" puis erreur : "Mutation score decreased: current=40.0% < baseline=50.0% (epsilon=0.1)" donc job échouée (comportement attendu)

	  - Note : la vérification est déclenchée par le workflow [.github/workflows/build.yml](.github/workflows/build.yml)/

5. Mise à jour sur `master`
	- Après validation on a mis à jour la baseline sur `master` à 40.0 pour éviter d'échouer immédiatement les builds existants.

Rappel : j'ai exécuté PIT localement avec la commande CI en forçant `-DargLine=""`. 

Sortie essentielle :
- PIT a généré 204 mutations et en a tué 85 — score brut ≈ 42% (affiché par PIT : "Generated 204 mutations Killed 85 (42%)").
- Rapport XML produit : `core/target/pit-reports/mutations.xml`
- Rapport HTML produit : `core/target/pit-reports/index.html`

## Problèmes rencontrés

En exécutant `bash ci/check-mutation-score.sh core` sur cette machine, le script a retourné un score courant égal à `0.0%`. Après vérification, la cause est un problème de parsing du rapport PIT:

- Les entrées dans `core/target/pit-reports/mutations.xml` utilisent des attributs avec des guillemets simples, par exemple `status='KILLED'`.
- Le script cherchait la chaîne `status="KILLED"` (guillemets doubles) et ne retrouvait donc aucun `KILLED` — `killed` était compté `0` et le script affichait `0.0%`.

## Solution

- Corriger `ci/check-mutation-score.sh` pour accepter indifféremment `status='KILLED'` et `status="KILLED"`. Exemple minimal (grep tolérant) :

grep -o "status=[\'\"]KILLED[\'\"]" core/target/pit-reports/mutations.xml | wc -l


- Alternative plus robuste : utiliser un parseur XML (`xmlstarlet` ou `xmllint`) pour compter les mutations tuées. Exemple :

xmlstarlet sel -t -v "count(//mutation[@status='KILLED' or @status=\"KILLED\"])" core/target/pit-reports/mutations.xml

- Après modification, re-tester localement :

bash ci/check-mutation-score.sh core

Cette correction à suffi; le parseur XML élimine toute fragilité liée au formatage.


# 2 - Ajout de nouveaux tests unitaires avec simulation de deux classes de GraphHopper via des mocks Mockito.

## Choix des classes testées (résumé)

- **Critères** : impact sur le module `core`, facilité d'isolation (mocks), et signalement par PIT (mutations survivantes).

- **Classes retenues** :

	- `com.graphhopper.storage.CHStorage` — grande surface de code critique; beaucoup de mutations trouvées par PIT. Moquer `Directory`/`DataAccess` permet tester des chemins sans manipuler de fichiers OSM.

	- `com.graphhopper.config.Profile` — ogique contenue et faible dépendance, permet couvrir les interactions publiques (hints/CustomModel) qui tuent des mutations logiques.

- **Mocks et comportements simulés** :
	- `CHStorage` : `Directory`, deux `DataAccess` (`nodesDA`, `shortcutsDA`) — `setInt/getInt` stub via `HashMap`, `setHeader`/`flush` no-op, vérification d'appels à `Directory.create(...)`.
	
	- `Profile` : `CustomModel`, `TurnCostsConfig` — vérifie que `setCustomModel` appelle `customModel.internal()` et le comportement de `putHint`.

- **Fichiers créés / modifiés** :
	- [core/src/test/java/com/graphhopper/config/ProfileMockitoTest.java](https://github.com/Aissatou26/graphhopper/blob/master/core/src/test/java/com/graphhopper/config/ProfileMockitoTest.java) — tests Mockito pour `Profile`.

	- [core/src/test/java/com/graphhopper/storage/CHStorageMockitoTest.java](https://github.com/Aissatou26/graphhopper/blob/master/core/src/test/java/com/graphhopper/storage/CHStorageMockitoTest.java) — tests Mockito pour `CHStorage`.

	- [core/pom.xml](https://github.com/Aissatou26/graphhopper/blob/master/core/pom.xml#L126-L132) — ajout de `mockito-core` (scope test) et inclusion de `${argLine}` dans `argLine` de Surefire.

	- [ci/check-mutation-score.sh](https://github.com/Aissatou26/graphhopper/blob/master/ci/check-mutation-score.sh#L1-L40) — script de vérification PIT (correction parsing quotes).

	- [ci/mutation-baseline.txt](https://github.com/Aissatou26/graphhopper/blob/master/ci/mutation-baseline.txt#L1) — baseline utilisée pour la comparaison.

	- [.github/workflows/build.yml](https://github.com/Aissatou26/graphhopper/blob/master/.github/workflows/build.yml#L67-L71) — intégration du contrôle et upload d'artefacts PIT/JaCoCo.


## Difficultés rencontrées

1) JaCoCo ne produisait pas `jacoco.exec` localement
- Constat : `jacoco.exec` manquait après les tests locaux.

2) PIT MINION_DIED dû à `${argLine}` littéral
- Constat : PIT minion échouait avec `ClassNotFoundException: ${argLine}` lors d'un run local.

3) Parsing XML par le script de vérification
- Constat : le script `ci/check-mutation-score.sh` renvoyait `0.0%` car il ne trouvait pas `status='KILLED'` (seulement `status="KILLED"` était recherché).

4) Tests d'intégration et dépendances lourdes
- Constat : certains tests historiques nécessitent des fichiers OSM ou des accès disque lourds, ce qui ralentit/fragilise la suite.

## Solutions appliquées / proposées

- JaCoCo : inclure `${argLine}` dans la configuration de Surefire pour préserver l'agent JaCoCo (permet à `prepare-agent` d'injecter `-javaagent` et d'écrire `jacoco.exec`). Voir `core/pom.xml` (bloc Surefire).

- PIT MINION_DIED : en contournement local, exécuter PIT avec `-DargLine=""` pour éviter de lancer minion avec une valeur littérale non résolue. En CI, s'assurer que l'environnement résout `${argLine}` correctement (workflow passe `-DargLine=""`).

- Parsing XML : rendre le parsing robuste en acceptant `status='KILLED'` et `status="KILLED"` (ex. `grep -o "status=[\\'\"]KILLED[\\'\"]"`) ou utiliser `xmlstarlet`/`xmllint` pour compter les mutations tuées.

- Tests lourds : mocker les accès bas‑niveau (`DataAccess`, `Directory`) et simuler en mémoire pour garder les nouveaux tests rapides et déterministes.

## Validation

- PIT local : run OK après `-DargLine=""`, sortie : "Generated 204 mutations Killed 85 (42%)".
- Script checker : après correction du parsing, le script renvoie le score correctement (42% >= baseline 40%).


# 3 - Ajout de Rickroll lors d'échecs de tests

## Objectif

Ajouter un élément d'humour dans la suite de test de GraphHopper : afficher un rickroll (avec liens et ASCII art) quand un cas de test échoue, via une GitHub Action réutilisable.

## Actions réalisées

### 1. Création de l'action réutilisable

**Fichier créé** : [.github/actions/rickroll-on-failure/action.yml](https://github.com/Aissatou26/graphhopper/blob/master/.github/actions/rickroll-on-failure/action.yml)

- **Rôle** : action composée (`composite`) qui affiche un message humoristique avec rickroll ASCII art et le lien emblématique vers la chanson de Rick Astley.
- **Déclenchement** : s'exécute uniquement si l'étape précédente échoue (condition `if: failure()`).
- **Contenu** :
  - Encadré ASCII art avec message humoristique
  - Lien vers `https://www.youtube.com/watch?v=dQw4w9WgXcQ` (rickroll classique)
  - Messages de motivation pour les devs ("Now go fix those bugs!")

### 2. Intégration dans le workflow CI

**Fichier modifié** : [.github/workflows/build.yml](https://github.com/Aissatou26/graphhopper/blob/master/.github/workflows/build.yml)

- **Deux points d'intégration** :
  
  1. **Job `build`** (après `mvn -B clean test`) :
    
     - name:  Rickroll on failure
       if: failure()
       uses: ./.github/actions/rickroll-on-failure
    
  
  2. **Job `mutation`** (après `bash ci/check-mutation-score.sh core`) :
    
     - name:  Rickroll on mutation failure
       if: failure()
       uses: ./.github/actions/rickroll-on-failure
    

### 3. Comportement

- **Quand les tests réussissent** : rien ne se passe, le workflow se termine normalement.
- **Quand un test échoue** : le rickroll s'affiche dans les logs GitHub Actions avec le message humoristique et le lien YouTube.
- **Quand PIT détecte une régression** : le rickroll s'affiche également pour le job de mutation testing.

## Fichiers modifiés/créés


| [.github/actions/rickroll-on-failure/action.yml](https://github.com/Aissatou26/graphhopper/blob/master/.github/actions/rickroll-on-failure/action.yml) | Créé | Action réutilisable avec message rickroll |
| [.github/workflows/build.yml](https://github.com/Aissatou26/graphhopper/blob/master/.github/workflows/build.yml) |  Modifié | Intégration des 2 étapes rickroll (build + mutation) |

## Avantages

 **Léger** : pas de dépendances externes, simple bash  
 **Réutilisable** : action composée, peut être utilisée dans d'autres workflows  
 **Humoristique** : élément de détente pour les développeurs  
 **Non-intrusif** : n'interfère pas avec le reste de la CI/CD  
 **Visible** : affichage dans les logs GitHub Actions accessibles à tous  

## Validation

- Structure validée : action.yml conforme aux standards GitHub Actions
- Intégration testée : workflow YAML syntaxiquement correct
- Déclenchement : `if: failure()` garantit l'exécution uniquement en cas d'échec







