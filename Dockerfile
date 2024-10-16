FROM azul/zulu-openjdk:17-latest
VOLUME /tmp
COPY ganttproject-builder/lib/*app.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
