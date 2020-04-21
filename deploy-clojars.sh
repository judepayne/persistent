clojure -A:jar persistent.jar
mvn deploy:deploy-file -Dfile=persistent.jar -DpomFile=pom.xml -DrepositoryId=clojars -Durl=https://clojars.org/repo/
