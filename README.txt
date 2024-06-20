

I. Prepare

In order to build, the 3rd party libraries needs to be installed into local repo. 
There are two options:

a) install into your local computer repo:
mvn initialize

b) install into your local shared repo at then commit:
mvn initialize  -gs $HOME/workspace/FLUTE-Maven-Repository/settings.xml


II. Build outside FLUTE network

II.a) Prepare shared repo

Outside FLUTE network checkout project and FLUTE-Maven-Repository
Then install into shared repo all dependencies:

mvn initialize                  -gs $HOME/workspace/FLUTE-Maven-Repository/settings.xml
mvn dependency:resolve          -gs $HOME/workspace/FLUTE-Maven-Repository/settings.xml
mvn dependency:resolve-plugins  -gs $HOME/workspace/FLUTE-Maven-Repository/settings.xml

Commit FLUTE-Maven-Repository to SVN.

II.b) Run build inside FLUTE network

Outside FLUTE network checkout project and FLUTE-Maven-Repository.

Run Maven commands, such as:
mvn install -gs $HOME/workspace/FLUTE-Maven-Repository/settings.xml
