# AnkiDroid Reviewer Guidelines

> [!TIP]
> Code reviewing is subjective, these guidelines are ONLY guidelines, use your best judgement

## Attitude

BE KIND! Make note of the good things in the PR and say thank you. Reviews are critical by nature, 
 and comments are likely to be read more critically than they are intended to be.
 

## New Contributor Onboarding

Relax these suggestions when working with new contributors, especially on pull requests (PRs) with the '**New Contributor**' label.

Pull Requests shouldn't make the code worse, but giving contributors a good first impression with the repo encourages them to stick around. Once a reasonable effort has been put in place, feel free to rebase a pull request to take it over the finish line, as long as this is communicated to the submitter, and you take on responsibility for future changes to get the PR merged.

## Finding Pull Requests


* [Sample query](https://github.com/ankidroid/Anki-Android/issues?q=state%3Aopen%20is%3Apr%20-label%3A%22Has%20Conflicts%22%20-label%3A%22Needs%20Author%20Reply%22%20draft%3Afalse%20-label%3A%22Blocked%20by%20dependency%22)
* [Unlabeled Rs](https://github.com/ankidroid/Anki-Android/issues?q=state%3Aopen%20is%3Apr%20no%3Alabel)


| **Description**               | **Query**                       | **Notes**                       |
|-------------------------------|---------------------------------|---------------------------------|
| Unlabeled PRs                 | `no:label`                      | Add 'Needs Review' immediately  |
| Review High Priority          | `label:"Review High Priority"`  |                                 |
| Needs Review                  | `label:"Needs Review"`          |                                 |
| Needs Second Approval         | `label:"Needs Second Approval"` |                                 |
| (exclude): Has Conflicts      | `label:"Has Conflicts"`         | Check occasionally              |
| (exclude): Needs Author Reply | `label:"Needs Author Reply"`    | Check occasionally for replies  |
| (exclude): Drafts             | `draft:false`                   | Ignore unless requested         |
| Needs reviewer reply          | `label:"Needs reviewer reply"`  |                                 |
| Pending Merge                 | `label:"Pending Merge"`         | Normally a reason it's unmerged |


## Things to look for

* Ensure the codebase is better after the change
* Ensure you understand the change
* Most PRs should be linked to an issue
* Ensure names are understandable
* Ensure appropriate documentation is added
* Ensure each commit compiles, does one thing, and has a good commit message
* Edge cases/thrown exceptions are handled appropriately
* Ensure fixes for regressions either have tests, or the `@NeedsTest` annotation
* Ensure significant functionality has tests, or `@NeedsTest`

Checkout the PR via [`gh pr checkout <num> --force`](https://cli.github.com/manual/gh_pr_checkout)

## Asking questions

It is expected that the submitter fully understands the code in their pull request.

Questions which you have about the implementation should likely result in additional documentation 
 and should be asked. If you're uncertain today, someone in the future with less context will be 
 even more uncertain.

## Before reviewing

* Request changes if the PR template isn't filled in
* Request changes if CI fails
  * Or rerun CI on a flake

## Requesting changes

* use `nit` for non-blocking comments/thoughts
* If a PR doesn't have sufficient evidence of testing (screenshots), feel free to block the PR on these
* If you are unsure on a decision, default to "implementer's choice"
* If you are requesting a large change, be sure that it's a meaningful and long-lasting improvements
* If a PR is too large, and seems feasible to split into smaller PRs, do so. Especially if smaller 
  refactoring commits can be cherry-picked.

Use the inline GitHub suggestions, or [create a patch](https://github.com/ankidroid/Anki-Android/wiki/Development-Guide/#creating-a-patch), linking to the documentation on [how to apply a patch](https://github.com/ankidroid/Anki-Android/wiki/Development-Guide/#applying-a-patch).

## Before merging

* Perform a strings sync if the 'Strings' label is set
* Ensure the [Licenses wiki](https://github.com/ankidroid/Anki-Android/wiki/Licences) is updated 
  if dependencies are added
    * A submitter **should** have added these
* Squash merge the branch (and force push) if the `squash-merge` label is set
* Ensure there are no merge commits in the history
* Add 'Queued for Cherry Pick to Stable Branch' for bugfixes to prioritize to go to stable

## Skipping 'second approval'

By default, each PR should be approved by two reviewers before merging. Situationally, this second approval step can be skipped. This should be to stop 'busywork' for a second reviewer, or for time-critical changes.

* Emergency changes
* Automated refactorings
* Test-only changes
* Changes which are obviously correct

## See also

* [Google: Code Review Standards](https://google.github.io/eng-practices/review/reviewer/standard.html)
