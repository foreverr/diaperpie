#!/bin/bash
rfkill unblock bluetooth
hciconfig hci0 up
hciconfig hci0 piscan
