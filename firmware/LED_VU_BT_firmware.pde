#define BUFFLEN  200
#define NUMLEDS  5
#define MAXVALUES 3

#define ACT_FADEUP    1
#define ACT_FADEDOWN  2
#define ACT_BLINK     3

int leds[] = {3, 5, 6, 9, 10};
int act[NUMLEDS];
unsigned char vals[NUMLEDS];
unsigned char act_dest[NUMLEDS];
unsigned long act_start[NUMLEDS];
unsigned long act_dur[NUMLEDS];
unsigned long period = 800;
char line[BUFFLEN];
int chan;
long values[MAXVALUES];
int battPin = A0;


void setup(){
  memset(vals, 0, NUMLEDS);
  memset(act, 0, NUMLEDS);
  memset(act_dur, 0, NUMLEDS);
  memset(act_dest, 0, NUMLEDS);
  memset(act_start, 0, NUMLEDS);
  Serial.begin(57600);
  for (int i=0;i<NUMLEDS;i++){
    digitalWrite(leds[i],HIGH);
    pinMode(leds[i], OUTPUT);
  }
  Serial.println("START");
}

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

void loop(){
  if (Serial.available() > 0){
    readline(0);
    parseLine();
    if ((chan < 0)||(chan > NUMLEDS-1)){
      Serial.println("ERRCH");
      return;
    }
    int batVal = 0;
    switch(line[0]){
    case 'S':      
      Serial.println("OK");
      setLed(chan,values[0]);
      vals[chan] = values[0];
      act[chan] = 0;
      break; 
    case 'F':
      act_dur[chan] = values[0];
      act_dest[chan] = (char)values[1];
      if (act_dest[chan] < vals[chan])
        act[chan] = ACT_FADEDOWN;
      else if (act_dest[chan] > vals[chan])
        act[chan] = ACT_FADEUP;
      act_start[chan] = millis();
      break;
    case 'B':
      batVal = analogRead(battPin);
      //full = 665
      //UVLO = 442
      Serial.print("VAL:");
      //Serial.println((int)ceil((((float)(batVal-422))/243.0)*100.0));
      //Serial.println((float)batVal/158.7);
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

void doBlink(){
  for (int i=0;i<NUMLEDS;i++){
    if (act[i] != 0){    //First check if there is anything to do at all
      switch (act[i]){
      case ACT_FADEUP:
        if (millis() > act_start[i]+act_dur[i]){
          act[i] = 0;
          setLed(i, act_dest[i]);
          vals[i] = act_dest[i];  
          break;
        }
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
        break;
      }
    }
  }
}

void setLed(int chan, int value){
  //int val = 1/(1+exp(((value/21)-6)*-1))*255;
  int val = value;
  analogWrite(leds[chan],255-val);
}

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
