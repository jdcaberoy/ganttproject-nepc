FROM azul/zulu-openjdk:17-latest
VOLUME /tmp
COPY biz.ganttproject.app.libs/lib/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
