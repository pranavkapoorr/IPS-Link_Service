# AltaPay-Link_Service

[Valitor](http://www.valitor.com) is a renowed name in the European Payment Industry. [International Payment Services Ltd.](http://ips-inter.com), a subcompany of Valitor has come up with new product to ease the payment industry tasks.
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
* AUTHORISED AltaPay Link License
```
### Installing

steps to use it:

```
* clone this repository
* build it using maven or can run it using any IDE of your choice
```

## Deployment

After building the project using [maven](https://maven.apache.org), the jar file generated in target folder should be run using terminal(bash or cmd) with ip_address and port as parameters to bind the AltaPayLink_Service to.
```
java -jar AltaPayLink_Service.jar 192.168.0.160 40001
```
Here is the epos which connects to their service.

![alt text](https://github.com/pranavkapoorr/AltaPay_Link_epos/blob/master/altapayepos.png?raw=true)

This product is [Valitor Group](http://www.valitor.com) property. This is the interface to connect to AltaPay Link service.

To get the EPOS go here -> [AltaPay_Epos](https://github.com/pranavkapoorr/AltaPay_Link_epos)

## Authors

* **Pranav Kapoor** - *Initial work* - [GITHUB](https://github.com/pranavkapoorr)
