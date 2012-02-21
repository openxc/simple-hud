//The maximum size of an incoming serial line
#define BUFFLEN  200
#define NUMLEDS  5
//The maximum number of arguments (comma delimited) in a message
#define MAXVALUES 3

//ENUM for possible LED actions
#define ACT_FADEUP    1
#define ACT_FADEDOWN  2
#define ACT_BLINK     3

//Mapping of LED to arduino pin number
int leds[] = {3, 5, 6, 9, 10};
//Stores the current LED status for each LED
//See ACT_FADEUP, ACT_FADEDOWN, ACT_BLINK
int act[NUMLEDS];
//The current LED intensity valies
unsigned char vals[NUMLEDS];
//The destination value for the action
unsigned char act_dest[NUMLEDS];
//The start time of the current action
unsigned long act_start[NUMLEDS];
//The curation of the current action
unsigned long act_dur[NUMLEDS];
unsigned long period = 800;
//The buffer for incoming serial data
char line[BUFFLEN];
//Serial message - the LED being acted upon
int chan;
//Serial message - the arguments
long values[MAXVALUES];
//The analog pin used to check battery level
int battPin = A0;

/*
 * Initialize arrays, start serial port,
 * set configure pin IO
*/
void setup(){
  memset(vals, 0, NUMLEDS);
  memset(act, 0, NUMLEDS);
  memset(act_dur, 0, NUMLEDS);
  memset(act_dest, 0, NUMLEDS);
  memset(act_start, 0, NUMLEDS);
  //Note we are using 57600 baud for the serial link,
  //we were noticing baud rate detection issues if higher baud rates are used
  Serial.begin(57600);
  for (int i=0;i<NUMLEDS;i++){
    digitalWrite(leds[i],HIGH);
    pinMode(leds[i], OUTPUT);
  }
  Serial.println("START");
}

/*
 * Once a line is read into the 'line' array, parse it for data
 * Data is stored into 'chan' and 'values'
*/
void parseLine(){
  char inputchan = line[1];
  chan = atoi(&inputchan);
  int idx = 2;
  int val = 0;
  int valpos = 0;
  char valbuff[BUFFLEN];
  char c = '\0';
  while ((idx < BUFFLEN)&&(val < MAXVALUES)){
    c = line[idx++];
    if ((c == ',')||(c == '\0')) {
      values[val++] = atol(valbuff);
      memset(valbuff, '\0', BUFFLEN);
      valpos = 0;
      if (c == '\0')
        break;
    } else {
      valbuff[valpos++] = c;
    }
  }
}

/*
 * The main program execution loop
 * Will parse serial data if recieved, and will blink LEDs
*/
void loop(){
  if (Serial.available() > 0){
	//Read into 'line' until we see a '\n' or 'M'
    readline(0);
    //Parse the line into 'chan' and 'values'
    parseLine();
    if ((chan < 0)||(chan > NUMLEDS-1)){
      Serial.println("ERRCH");
      return;
    }
    int batVal = 0;
    switch(line[0]){
    case 'S':     
      //SET - directly set the value for that channel,
	  //and turn off any actions that might have been running 
      Serial.println("OK");
      setLed(chan,values[0]);
      vals[chan] = values[0];
      act[chan] = 0;
      break; 
    case 'F':
      //FADE - set a destination value for a given channel, and slowly
	  //fade to that value over the specified duration
      act_dur[chan] = values[0];
      act_dest[chan] = (char)values[1];
      if (act_dest[chan] < vals[chan])
        act[chan] = ACT_FADEDOWN;
      else if (act_dest[chan] > vals[chan])
        act[chan] = ACT_FADEUP;
      //Store the current time as the start time
      act_start[chan] = millis();
      break;
    case 'B':
      //BATTERY - read raw battery level
      batVal = analogRead(battPin);
      //approximate 100% value = 665
      //approximate 0% value = 442
      Serial.print("VAL:");
	  //A formula for approximate battery level in percentage
      //Serial.println((int)ceil((((float)(batVal-422))/243.0)*100.0));
      //Serial.println((float)batVal/158.7);
      //unused, since these values have not been tested enough to be hardcoded
      Serial.println(batVal);
      break;   
    case 'P':
      Serial.println("ACK");
    default:
      Serial.print("ERRCO");
      Serial.println(line[0],HEX);
    }
  }
  doBlink();
}

/*
 * Preform actions on any LEDs that need to be acted upon (not just blink)
*/
void doBlink(){
  for (int i=0;i<NUMLEDS;i++){
    if (act[i] != 0){    //First check if there is anything to do at all
      switch (act[i]){
      case ACT_FADEUP:
		//If we are beyond the duration of the fade, set the LED
        //to the destination value and turn off the activity
        if (millis() > act_start[i]+act_dur[i]){
          act[i] = 0;
          setLed(i, act_dest[i]);
          vals[i] = act_dest[i];  
          break;
        }
		//Otherwise, set the LED to a value proportional to the remaining time
        setLed(i,((((millis()-act_start[i])*(act_dest[i]-vals[i])))/act_dur[i])+vals[i]);
        break;
      case ACT_FADEDOWN:
        if (millis() > act_start[i]+act_dur[i]){
          act[i] = 0;
          setLed(i, act_dest[i]);
          vals[i] = act_dest[i];
          break;
        }
        setLed(i,((((act_start[i]+act_dur[i])-millis())*(vals[i]-act_dest[i]))/act_dur[i])+act_dest[i]);
        break;
      case ACT_BLINK:
		//TODO - implement blink!
        break;
      }
    }
  }
}

/*
 * Set a LED to a given value immediately.
*/
void setLed(int chan, int value){
  //For a more natural, logarythmic-emulated curve, use this formula
  //int val = 1/(1+exp(((value/21)-6)*-1))*255;
  //This wasn't used due to the low timer resolution (only 8-bit)
  //It caused low intensities to be extremely choppy, which didn't fade well
  int val = value;
  analogWrite(leds[chan],255-val);
}

/*
 * Read 1 line from the serial port, blocking execution until \n or M
 * 'echo' can be set to echo every recieved character back
 * This is useful for debugging using minicom
*/
void readline(char echo){
  int i=0; char c = 0x0;
  memset(line,'\0',BUFFLEN);
  while((i < BUFFLEN-1)){
    while (Serial.available() < 1) {}  //Block until character is available
    c = Serial.read();
    if (echo)
      Serial.print(c,BYTE);
    //if (c == '\r')
    //  continue;
    //if (c == '\n')
    if ((c == '\n')||(c == '\r')||(c == 'M'))
      break;
    line[i++] = c;
  }
}
