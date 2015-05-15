#!/usr/bin/env python

class I2c:
    dd=0;
    def __init__(self, n=0):
        self.n=n

    def address(self, adr):
        self.adr = adr
    def readReg(self, reg):
        self.reg = reg
        self.dd=self.dd+1;
        return self.dd
    def writeReg(self, reg, data):
        self.reg = reg
        self.data = data
    def readWordReg(self, reg):
        self.reg = reg
        self.dd=self.dd+1;
        return self.dd
    def write(self, arry):
        self.arry = arry
    def writeByte(self, byte):
        self.byte = byte
    def read(self, byte):
        self.byte = byte
        #xx=b'\xAB\xCD'
        xx='AB'
        return xx;
        #return xx.to_bytes(2,byteorder='little')

DIR_OUT=1

class Gpio:
    def __init__(self, pin=0):
        self.pin=pin
    def dir(self, mode=0):
        self.mode=mode
    def write(self, out):
        self.out=out

class Aio:
    xx=0
    def __init__(self, pin=0):
        self.pin=pin
    def read(self):
        self.xx=self.xx+1
        return self.xx
