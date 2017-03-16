* Coverage:
    - In Eclipse, installed EclEmma, by Help -> Eclipse Marketplace, and
      searching for it.
      When it's installed, there'll be an extra run button to the left of
      the normal one. Pressing 

main:ids/Identifier.java          done
main:ids/IdType.java              done
main:ids/IdDb.java                done

main:ids/IdSet.java
main:ids/IdNonVersionSet.java
main:ids/IdVersionSet.java


main:ids/IdResolver.java
main:ids/RequestId.java
main:ids/IdDbJsonReader.java

test:ids/test/TestIdResolver.java
test:ids/test/AllTests.java
test:ids/test/Helper.java
test:ids/test/TestIdDb.java
test:ids/test/TestIdType.java
test:ids/test/TestIdentifier.java
test:ids/test/TestRequestId.java
test:ids/test/TestIdSet.java
  ```
  parent:
    pmid:123456
    pmcid:654321
    doi:10.13/23434.56
  kid0:
    pmid:123456.1
    pmcid:654321.2
  kid1:  current
    pmid:123456.3
    mid:NIHMS77876
  kid2:
    pmcid:654321.8
    aiid:654343
  ```


main:cite/App.java
main:cite/BadParamException.java
main:cite/CitationProcessor.java
main:cite/CiteException.java
main:cite/CiteprocPool.java
main:cite/ConvAppNxmlItemSource.java
main:cite/ExceptionHandler.java
main:cite/ItemProvider.java
main:cite/ItemSource.java
main:cite/MainServlet.java
main:cite/NotFoundException.java
main:cite/Request.java
main:cite/ServiceException.java
main:cite/StcacheNxmlItemSource.java
main:cite/StcachePubOneItemSource.java
main:cite/TestItemSource.java
main:cite/Transform.java
main:cite/TransformEngine.java
main:cite/WebServer.java
main:cite/package-info.java
main:cite/PubmedPubOneItemSource.java


test:cite/test/RequestTestCase.java
test:cite/test/TestApp.java
test:cite/test/TestCitationProcessor.java
test:cite/test/TestRequestSimple.java
test:cite/test/TestRequests.java
test:cite/test/TestTransformSimple.java
test:cite/test/TestTransforms.java
test:cite/test/TransformTestCase.java
test:cite/test/Utils.java
