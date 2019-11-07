# Scriptable web framework

## Setting up new project

To quickly setup new scriptable project named projname, run:
    `./setup-new-project.sh projname`

A few related notes:
  - when manually adding external repository to be included as part of the project's source tree as opposed to as submodule,
    terminate sub-repository's name with a slash, e.g. `git add scriptable/`. This way it'll be possible to manage
    dependency's source code just as any other project's files while still being able to synchronize sub-repository
    with its upstream as needed.

  - note on placing scriptable.jar in Tomcat's common lib directory:
    scriptable.jar must not be placed in tomcat's lib directory. There's couple of reasons for that:
    - In this case it would be loaded by the Tomcat's root class loader which obviously has no access to the libraries
      bundled with the application. And one of scriptable.jar responsibilities is to load precompiled
      JS classes from the app's war file.
    - Another issue would be the custom logging facilities defined in the application's logging.properties
      would not be available, hence no log messages.

