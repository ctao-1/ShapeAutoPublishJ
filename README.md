在项目根目录执行：
//cd ShapeAutoPublish(optional)
mvn -q clean package
java -jar target\geoserver-publisher-1.0.0-jar-with-dependencies.jar

Show possible problems:
Problem1:  the compiler can't find the JsonNode type (missing import or missing dependency). 
Fixes:  Check pom.xml, change your configuration of dependency, such as the version of your JDK and jackson, and run mvn -U clean package.If don't have problems or still see the error after adding the dependency, try invalidating caches and restarting your IDE.
