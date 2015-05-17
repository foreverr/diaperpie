#!/bin/bash
rfkill unblock bluetooth
sleep 3
hciconfig hci0 up
hciconfig hci0 piscan
