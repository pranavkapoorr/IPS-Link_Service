# IPS-Link_Service

IPS-Link-Service is the new service based solution of [RQACS Inc.](https://rqacs.com) making integration with the Payment device easier.
This solution acts as the "Link" between the EPOS and the Payment Device. This solution runs as tcp service which accepts the various function requests as Json string and generates a Json response with various fields giving information about the transaction made.

 It provides various operations that can be done on the payment device using it. Some of the functionalities it provides are:

* Payment
* Reversal
* Refund
* PED Balance (X Reports)
* End of Day (Z Reports)
* First DLL
* Update DLL
* PED Status
* Probe PED
* Last Transaction Status
* Reprint Receipt
* Wait For Card Removed

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy the project on a live system.

### Prerequisites

things you need to install the software:

```
* JDK 8
* AUTHORISED IPS Link License
```
### Installing

steps to use it:


```
* clone this repository
* build it using maven or can run it using any IDE of your choice
```

## Deployment

After building the project using [maven](https://maven.apache.org), the jar file generated in target folder should be run using terminal(bash or cmd) with ip_address and port as parameters to bind the IPSLink_Service to.

```
java -jar IPS-Link_Service.jar 192.168.0.160 40001
```
Here is the epos which connects to their service.

![alt text](https://github.com/pranavkapoorr/IPS-Link_Service/blob/complete_refactor/resources/epos_ips_link.png)

This product is [RQACS Inc.](https://www.rqacs.com) property. This is the interface to connect to IPS Link service.

To get the EPOS go here -> [IPS-Link_Epos](https://github.com/pranavkapoorr/IPS_Link_epos)

## Payment Devices Supported
```
* Ingenico
* Pax
```
## Authors

* **Pranav Kapoor** - *Initial work* - [pranavkapoor](https://bitbucket.org/pranavkapoorr_ips)
