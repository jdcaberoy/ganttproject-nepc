FROM azul/zulu-openjdk:17-latest
VOLUME /tmp
COPY ganttproject-builder/lib/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
