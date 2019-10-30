# Sample Scriptable app

## Assumptions

Sample configuration is for recent tomcat/fedora. Local user should be part of tomcat group.
Adjust paths as needed in build.properties

## Build

For initial development instance setup, follow these steps:

  1. `git clone /path/to/repo.git`
  2. `cp lib/scriptable-deps/build/_*.jar /usr/share/tomcat/lib`
  3. `systemctl restart tomcat`
  4. Make sure your source directory is readable by tomcat user
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

