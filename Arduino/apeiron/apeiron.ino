/*
*
*    Apeiron - Arduino UNO
*    by Hector Centeno (info@hcenteno.net - www.hcenteno.net)
*    Requires: SoftTimer library and PciManager library
*
*    Apeiron is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*
*/

#include <SoftTimer.h>
#include <Task.h>
//#include <DistanceGP2Y0A21YK.h>

const int numReadings = 20;

int readings[numReadings];      // the readings from the analog input
int index = 0;                  // the index of the current reading
int total = 0;                  // the running total


int led1Pin = 9;
int led2Pin = 10;
int motor1pin = 5;
int motor2pin = 6;
int analogPin = 3;

int distance;
bool alertMode;

int val = 0;

const int FIRST_LIMIT = 70; // Distance threshold to start affecting the lighting
const int SECOND_LIMIT = 150; // Distance threshold to start vibration
const int LEDS_ALERT_PERIOD = 5;
const int LEDS_IDLE_PERIOD = 20;

long led1Step = 1;
long led2Step = 1;

int led1Level = 0;
int led2Level = 0;
int led1Dir = 1;
int led2Dir = 1;

char padding[2];

bool motorState = false;

//DistanceGP2Y0A21YK distSensor;

Task distCheckTask(50, distanceCheck);
Task dimLED1Task(LEDS_IDLE_PERIOD, dimLED1);
Task dimLED2Task(LEDS_IDLE_PERIOD, dimLED2);
Task vibrateTask(50, vibrateMotors);


void setup()
{
  Serial.begin(115200);
  pinMode(led1Pin, OUTPUT);
  pinMode(led2Pin, OUTPUT);
  pinMode(motor1pin, OUTPUT);
  pinMode(motor2pin, OUTPUT);
  
  for (int thisReading = 0; thisReading < numReadings; thisReading++)
    readings[thisReading] = 0;

//  distSensor.begin(A0);

  SoftTimer.add(&distCheckTask);
  SoftTimer.add(&dimLED1Task);
  SoftTimer.add(&dimLED2Task);
  SoftTimer.add(&vibrateTask);

}

void vibrateMotors(Task* task) {
  
  if (motorState) {
    if ((int)random(0, 2) == 0) {
      digitalWrite(motor1pin, LOW);
    } else {
      digitalWrite(motor1pin, HIGH);
    }

    if ((int)random(0, 2) == 0) {
      digitalWrite(motor2pin, LOW);
    } else {
      digitalWrite(motor2pin, HIGH);
    }
  } else {
    digitalWrite(motor1pin, LOW);
    digitalWrite(motor2pin, LOW);
  }
}


void distanceCheck(Task* task) {
  distance = average(analogRead(A0));
    
  Serial.print("#");
  sprintf(padding, "%03d", distance);
  Serial.print(padding);

  if (distance > FIRST_LIMIT) {
    
    alertMode = true;
    
    led1Step = map(distance, FIRST_LIMIT, SECOND_LIMIT, 0, 20);
    led2Step = map(distance, FIRST_LIMIT, SECOND_LIMIT, 0, 20);
    
    dimLED1Task.setPeriodMs(LEDS_ALERT_PERIOD);
    dimLED2Task.setPeriodMs(LEDS_ALERT_PERIOD);
    
    if (distance > SECOND_LIMIT) {
      motorState = true;
    } else {
      motorState = false;
    }
  } else {
    motorState = false;
    digitalWrite(motor1pin, LOW);
    digitalWrite(motor2pin, LOW);
    led1Step = 1;
    led2Step = 1;
    alertMode = false;
    dimLED1Task.setPeriodMs(LEDS_IDLE_PERIOD);
    dimLED2Task.setPeriodMs(LEDS_IDLE_PERIOD);
  }
}

void dimLED1(Task * task) {

  led1Level = led1Level + (led1Dir * led1Step);
  if (led1Level > 255) {
    led1Dir = -1;
    led1Level = 254;
  }
  if (led1Level < 0) {
    led1Dir = 1;
    led1Level = 1;
  }

  analogWrite(led1Pin, led1Level);
}

void dimLED2(Task * task) {

  led2Level = led2Level + (led2Dir * (random(0, 2) + led2Step));
  if (led2Level > 255) {
    led2Dir = -1;
    led2Level = 254;
  }
  if (led2Level < 0) {
    led2Dir = 1;
    led2Level = 1;
  }

  analogWrite(led2Pin, led2Level);
}

int average(int input) {
 // subtract the last reading:
  total= total - readings[index];         
  // read from the sensor:  
  readings[index] = input; 
  // add the reading to the total:
  total= total + readings[index];       
  // advance to the next position in the array:  
  index = index + 1;                    

  // if we're at the end of the array...
  if (index >= numReadings)              
    // ...wrap around to the beginning: 
    index = 0;                           

  // calculate the average:
  return total / numReadings;  
}

