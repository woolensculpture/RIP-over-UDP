Author: Benjamin Reynolds
ID: bbr6221
Usage: java -jar rip2sim.jar [-v] [configuration files or directories holding configuration files]

To Build:
cd src
javac -d ../build *.java
cd ../build
jar -cvfm networksimulator.jar ../src/META-INF/MANIFEST.MF *.class

Notes: