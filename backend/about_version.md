## release workflow:

     rest/web same versioning
     check appliaction.properties 
     bump pom.xml version
     bump build_deploy.sh versions 

## 1.3
Released: 15.2.2024

    endpoints for dividends, stats, companies views
    added shares float and sector to companies
    financials data for companies
    fixes & refactoring

## 1.2
Released: 28.1.2024

    create/sell trade endpoints
    latest values for records
    firebase service to get realtime data + push active trades
    fixes

## 1.1
Released: 22.1.2024

    added watching company and record ps ratio to sql schemas
    endpoints for creating and updating records
    company info endpoint

## 1.0
Released: 11.1.2024

    sql schemas for companies, trades and records
    dao, service and rest layers for companies, trades and records
    script to generate sql inserts
    build & deploy configs