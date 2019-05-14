# Sample Project

## Build

For initial development instance setup, follow these steps:

  1. `git clone /path/to/repo.git`
  4. `ant`, when prompted choose your dev instance context, e.g. /cd-and

For subsequent builds, use `ant` or `ant clean-build`

## Release

  - ant release-test
  - ant release-prod

## Handy git commands

  - push local tags to remote: git push --tags
  - delete local tag: git tag -d tag_name
  - delete tag in remote: git push --delete origin tag_name
  - syncronize tags with remote: git fetch -p -P

