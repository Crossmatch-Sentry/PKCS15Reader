# PKCS15Reader

Copyright 2018 Crossmatch Technologies, Inc. All rights reserved

Sample program to illustrate reading from a contact smartcard on the Crossmatch Verifier Sentry device using the pcks15-tool that is built-in to the Crossmatch Sentry device. 

Given a PKCS#15 compliant smartcard like a CAC card, TWIC card, etc. the user can dump all the containers on the card and/.or display the fingerprint container itself.

## Running the program:

Launch the program and insert a PKCS#15 compliant smartcard into the contact reader on the Sentry. When the card is detected, it will prompt for the card users PIN which is used to unlock some of the protected containers on the card. The buttons on screen will be enabled and they will dump the selected contents of the card on-screen.

## Details

This program uses the built-in pkcs15-tool on the Sentry device to abstract out the details of the smartcard to provide a high level interface. See the pkcs15-tool and manual pages for further information.

The pkcs15-tool is invoked using Androids ProcessBuilder.

As long as the smartcard you are trying to read is PKCS#15 compliant, such as a Javacard, CAC Card, TWIC card, etc. It should be able to read the containers on the card directly. This requires some understanding of the underlying card layout.

You can run the same command on the Sentry device from a cmd shell or adb shell to see the results of the output you can expect.

## pkcs15-tool 

The pkcs15-tools that is built-in to the Sentry device is a standard Linux command line program to manipulate PKCS #15 data structures on smart cards. 

You can run this program directly from a command shell on the Sentry device. You can get this via adb like:

  $ adb shell
  shell@wahoo:/ $ pkcs15-tool -h 

Example to dump fingerprint container:

  $ pkcs15-tool  --pin xxxxxxxx --verify-pin --read-data-object 2.16.840.1.101.3.7.2.96.16

where xxxxxxxx is the card user's PIN code to unlock the card.

## Future 

We could/should create a Java JNI wrapper around the underlying library rather than relying on the pkcs15-tool interface but both will work.
