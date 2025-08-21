在项目根目录执行：
<br>//cd ShapeAutoPublish(optional)
<br>mvn -q clean package
<br>java -jar target\geoserver-publisher-1.0.0-jar-with-dependencies.jar

<br>Show possible problems:
<br>Problem1:  the compiler can't find the JsonNode type (missing import or missing dependency). 
<br>Fixes:  Check pom.xml, change your configuration of dependency, such as the version of your JDK and jackson, and run mvn -U clean package.If don't have problems or still see the error after adding the dependency, try invalidating caches and restarting your IDE.
