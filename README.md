# scriptable-libs

Note on placing scriptable.jar in Tomcat's common lib directory
-----------

scriptable.jar must not be placed in tomcat's lib directory. There's couple of reasons for that:

- In this case it would be loaded by the Tomcat's root class loader which obviously has no access to the libraries
  bundled with the application. And one of scriptable.jar responsibilities is to load precompiled
  JS classes from the app's war file.
- Another issue would be the custom logging facilities defined in the application's logging.properties
  would not be available, hence no log messages.

