# th2 act UI-Framework (web) demo (3.4.0)

This is a project to demonstrate Act implementation to interact with Hand in web mode.
This project contains actions which
1) go to act-ui, set connectivity session fields, add message data to the editor and extract results
2) go to the report-viewer (via provided in step 1 link) and extract fix raw message from it
3) go to the report-viewer and search other message and extract fix raw message from it

## Requirements
+ Installed act-ui 
+ Setup https support in act-ui and rpt-data-viewer. This is a requirement for extracting data from clipbaord

## Configuration
This box should be configured as default act boxes.
Custom config contains:
* `act_url` - Url to deployed act-ui (should have https protocol)

Example:
```
spec:
  custom-config:
    act_url: 'https://th2-cluster:30443/th2-hand/act-ui/'
```

## Release Notes

### 3.4.0
+ renamed project to th2-act-uiframework-web-demo
+ updated act-gui-core dependency

### 3.3.2
+ fixed issues interacting with UI elements
+ fixed error when th2-hand doesn't return expected url 

### 3.3.1
+ fixed issues interacting with UI elements

### 3.3.0
+ renamed project to th2-act-ui-framework-web-demo
+ updated README.MD
+ adopt it to new version of rpt-viewer (3.1.54) and act-ui (1.0.17)

### 3.2.0

+ reads dictionaries from the /var/th2/config/dictionary folder.
+ uses mq_router, grpc_router, cradle_manager optional JSON configs from the /var/th2/config folder
+ tries to load log4j.properties files from sources in order: '/var/th2/config', '/home/etc', configured path via cmd, default configuration
+ update Cradle version. Introduce async API for storing events
+ removed gRPC event loop handling
+ fixed dictionary reading