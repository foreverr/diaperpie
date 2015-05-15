#!/usr/bin/env python

#include <Wire.h>
import mraa as m
import time
import math

LED_OUT=13
ADR_BMG160=0x68
ADR_BMA2x2=0x18
ADR_BMM055=0x10

class water_sensor:
    def __init__(self):
        self.pin=m.Aio(1)   #A1 pin
    def get(self):
        return self.pin.read()

class temp_sensor:
    def __init__(self):
        self.pin=m.Aio(0)   #A0 pin
    def get(self):
        #int B=3975;
        #a=analogRead(0);
        #resistance=(float)(1023-a)*10000/a; //get the resistance of the sensor;
        #temperature=1/(log(resistance/10000)/B+1/298.15)-273.15;//convert to temperature via datasheet ;
        a=self.pin.read();
        B=3975
        resistance=(float)(1023-a)*10000/a #//get the resistance of the sensor;
        temperature=1/(math.log(resistance/10000)/B+1/298.15)-273.15 #//convert to temperature via datasheet ;
        return temperature

class bmx055:   #I2C pin
    aid=0
    gid=0
    mid=0
    ax=0; ay=0; az=0
    gx=0; gy=0; gz=0
    mx=0; my=0; mz=0
    def __init__(self):
        self.x = m.I2c(0)

    def delay(self, xx):
        time.sleep(xx/1000.0)

    def I2C_Read(self, adr,reg):
        self.x.address(adr)
        return self.x.readReg(reg)


    def I2C_ReadUINT(self, adr, reg):
        return self.I2C_Read(adr,reg)|(self.I2C_Read(adr,reg+1)<<8)

    def I2C_Write(self, adr, reg, data):
        self.x.address(adr)
        self.x.writeReg(reg, data)

#=============================================================
    def init_gyr(self):
        self.gid=self.I2C_Read(ADR_BMG160,0x00);  #g-ID
        #print("G-ID:%02X\n"%self.gid)
        self.I2C_Write(ADR_BMG160,0x11,0x01);  #LPM1
        self.I2C_Write(ADR_BMG160,0x12,0x02);  #LPM2
        self.I2C_Write(ADR_BMG160,0x10,0x01);  #BW: 230Hz
        return self.gid

    def get_gyr(self):
        self.gx=self.I2C_ReadUINT(ADR_BMG160,0x02);  #g-x
        self.gy=self.I2C_ReadUINT(ADR_BMG160,0x04);  #g-y
        self.gz=self.I2C_ReadUINT(ADR_BMG160,0x06);  #g-z
        return self.gx, self.gy, self.gz

#=============================================================
    def init_acc(self):
        self.aid=self.I2C_Read(ADR_BMA2x2,0x00);  #A-ID
        #print("A-ID:%02X\n"%self.aid)  #default:0xFA
        self.I2C_Write(ADR_BMA2x2,0x12,0x03)  #PMU_LOW_NOISE: LPM1, power mode=0
        self.delay(1) #ms
        self.I2C_Write(ADR_BMA2x2,0x11,0x82)  #PMU_LPW: Normal + ?ms
        self.delay(1) #ms
        self.I2C_Write(ADR_BMA2x2,0x3E,0x01)  #FIFO_CONFIG_1: bypass, x+y+z
        self.delay(1) #ms
        self.I2C_Write(ADR_BMA2x2,0x11,0x04)  #PMU_LPW: Normal + ?ms
        self.delay(1) #ms
        self.I2C_Write(ADR_BMA2x2,0x10,0x08)  #PMU_BW: 7.81Hz
        return self.aid

    def I2C_Read12bit(self, adr, reg):
        xx=(self.I2C_Read(adr,reg)>>4)|(self.I2C_Read(adr,reg+1)<<4)
        if xx&(1<<11):
            #xx|=0xF000
            xx=xx-0x1000
        return xx


    def get_acc(self):
        self.ax=self.I2C_Read12bit(ADR_BMA2x2,0x02)
        self.ay=self.I2C_Read12bit(ADR_BMA2x2,0x04)
        self.az=self.I2C_Read12bit(ADR_BMA2x2,0x06)
        return self.ax, self.ay, self.az

#=============================================================
    def init_mag(self):
        self.mid=self.I2C_Read(ADR_BMM055,0x40);  #M-ID
        #print("M-ID:%02X\n"%self.mid);  #default:0x32
        #init
        #  xx=I2C_Read(ADR_BMM055,0x4B);  #
        #  I2C_Write(ADR_BMM055,0x4B,xx|0x01);  #
        xx=self.I2C_Read(ADR_BMM055,0x4B)
        #print("M(0x4B):%02X\n"%xx)

        self.I2C_Write(ADR_BMM055,0x4B,0x83)
        self.delay(2) #ms
        self.I2C_Write(ADR_BMM055,0x4B,0x01)
        self.delay(2) #ms
        '''
        #init_trim_reg
        xx=I2C_Read(ADR_BMM055,0x5D);  #x1
        xx=I2C_Read(ADR_BMM055,0x5E);  #y1
        xx=I2C_Read(ADR_BMM055,0x64);  #x2
        xx=I2C_Read(ADR_BMM055,0x65);  #y2
        xx=I2C_Read(ADR_BMM055,0x71);  #xy1
        xx=I2C_Read(ADR_BMM055,0x70);  #xy1
        I2C_ReadUINT(ADR_BMM055,0x6A);  #z1
        I2C_ReadUINT(ADR_BMM055,0x68);  #z2
        I2C_ReadUINT(ADR_BMM055,0x6E);  #z3
        I2C_ReadUINT(ADR_BMM055,0x62);  #z4
        I2C_ReadUINT(ADR_BMM055,0x6C);  #xyz1
        '''
        self.I2C_Write(ADR_BMM055,0x4C,0x02)  #10Hz
        self.I2C_Write(ADR_BMM055,0x51,0x04)  #
        self.I2C_Write(ADR_BMM055,0x52,0x0E)  #
        self.I2C_Write(ADR_BMM055,0x4C,0x10)  #
    #  I2C_Write(ADR_BMM055,0x4C,0x3D)  #xx
        return self.mid

    def I2C_Read13bit(self, adr, reg):
        xx=(self.I2C_Read(adr,reg)>>3)|(self.I2C_Read(adr,reg+1)<<3)
        if xx&(1<<12):
            #xx|=0xE000
            xx=xx-0x2000
        return xx


    def get_mag(self):
        self.mx=self.I2C_Read13bit(ADR_BMM055,0x042)
        self.my=self.I2C_Read13bit(ADR_BMM055,0x044)
        self.mz=self.I2C_Read13bit(ADR_BMM055,0x046)
        return self.mx,self.my,self.mz

#=============================================================
    def init_acc_gyr_mag(self):
        return self.init_acc(), self.init_gyr(), self.init_mag()

    def get_acc_gyr_mag(self):#  Serial.print("loop#1\n")
        return (self.get_acc()),(self.get_gyr()),(self.get_mag())

if __name__ == "__main__":
    print("main...")
    """
        #pinMode(LED_OUT, OUTPUT); #LED
        self.pin=m.Gpio(LED_OUT)
        self.pin.dir(m.DIR_OUT)
        self.ledc=0 #led count
        self.ledc=self.ledc+1
        if self.ledc&1:
            #digitalWrite(LED_OUT, HIGH);    # LED on
            self.pin.write(1)
        else:
            #digitalWrite(LED_OUT, LOW);    # LED off
            self.pin.write(0)
        self.delay(200); #ms
    """
    #------------------------------------------
    mm=bmx055()
    mm.init_acc_gyr_mag()
    (ax,ay,az),(gx,gy,gz),(mx,my,mz)=mm.get_acc_gyr_mag()
    print (ax,ay,az),(gx,gy,gz),(mx,my,mz)

    #Water sensor
    x=water_sensor() #init
    print "Water:", x.get() #data

    #Temp sensor
    x=temp_sensor() #init
    print "Temp:",x.get() #data
