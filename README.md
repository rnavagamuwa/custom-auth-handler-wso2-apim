# Custom Authentication Handler to achieve service chaining with WSO2 API Manager

This repository contains a customer handler implementation for WSO2 API Manager. This uses the validation service 
implemented in [https://github.com/rnavagamuwa/service-chaining-mock-services](https://github.com/rnavagamuwa/service-chaining-mock-services) 
repository.

## Building the handler
- The project can be built via maven by executing `mvn clean install`.
- Or else the jar file can be found in `target/` directory

Please refer to [this](https://apim.docs.wso2.com/en/latest/develop/extending-api-manager/extending-gateway/writing-custom-handlers/)
documentation to get more information on deploying the custom handler on WSO2 API Manager.