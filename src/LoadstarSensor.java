
/*
Class-based Java Serial Communication

Written by Siddhartha Nair
9/16/2022

Uses the jserialcomm package to read data through the serial port

Works with DI-100, DI-1000, and Iload Cells
(only reads weight from iLoad Cells))
*/

import com.fazecast.jSerialComm.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.Date;
import java.util.Arrays;
import java.text.SimpleDateFormat;

public class LoadstarSensor{
    
    private final String _portName;
    private final int _baud;
    private final String _model;
    private final String _id;
    protected final SerialPort _thisPort;
    private final String _o0w1; //Single Response
    private final String _o0w0; //Contious Response
    private final String _units;
    private final String _type;
    private final SimpleDateFormat _formatter;

    public LoadstarSensor(String portName, int baud) throws Exception
    {
        this._portName = portName;
        this._baud = baud;

        this._formatter = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");

        SerialPort port = SerialPort.getCommPort(portName);
        port.setComPortParameters(baud, 8, 
                                    SerialPort.ONE_STOP_BIT, 
                                    SerialPort.NO_PARITY);
        port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

        this._thisPort = port;

        //Sends commands to read the model and id from the sensor
        TryOpen();
        this._model = GetCommandResponse("MODEL");
        this._id = GetCommandResponse("SS1");

        //Checks the units, units type, and command of the sensor

        //Checking for FCM Sensors
        if (this._model.contains("DI-100")){
            //if the sensor is using a DI-100 or DI-1000, the command for 
            //continuous data is "WC", and "W" for a single response
            this._o0w0 = "WC\r";
            this._o0w1 = "W\r";
            this._units = GetCommandResponse("UNITS");
            if (this._id.startsWith("DISP")){
                this._type = "Displacement";
            }else if(this._id.startsWith("TEMP")){
                this._type = "Temperatre";
            }else if (this._id.startsWith("VM")){
                this._type = "Voltage";
            }else if(this._id.startsWith("TQ")){
                this._type = "Torque";
            }else{
                this._type = "Force";
            }
        }
        //Checking for Iload cells
        else{
            //if the sensor is capacitive, the command for 
            //continuous data is "O0W0", and "O0W1" for a single response
            this._o0w0 = "O0W0\r";
            this._o0w1 = "O0W1\r";

            //Capacity Loadcells can read Force and Temperater, or Force or Temperature exclusively
            if (this._id.startsWith("FT")){
                this._type = "Force and Temperatre";
                this._units = "mLB/C";
            }else if(this._id.startsWith("TEMP")){
                this._type = "Temperatre";
                this._units = "C";
            }else{
                this._type = "Force";
                this._units = "mLB";
            }
        }

        //close the port when the program is closed
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {port.closePort(); }));
    } 

    /*
    if the port is closed, open the port
     */
    protected void TryOpen(){
        if (!_thisPort.isOpen()){
            _thisPort.openPort();
        }
    }

    /*
    if the port is open, close the port 
     */
    protected void TryClose(){
        if (_thisPort.isOpen()){
            _thisPort.closePort();
        }
    }

    protected void SerialPortSlowWrite(String str, int delay) throws InterruptedException{
        _thisPort.flushIOBuffers();
        _thisPort.writeBytes(str.getBytes(StandardCharsets.UTF_8), str.getBytes(StandardCharsets.UTF_8).length);
        Thread.sleep(delay);
    }

    /*
    DataListener listens are incoming data through the serial port
    When data comes in a SerialPortEvent is raised which causes the code inside the 
    serialevent method to run with the incoming buffer as the argument
    */
    protected void AddSerialEventListener(SerialPort port){
        port.addDataListener(new SerialPortDataListener(){

            @Override
            public int getListeningEvents(){
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }

            @Override
            public void serialEvent(SerialPortEvent event){
                if(port.bytesAvailable() <= 13){
                    return;
                }
                byte[] newData = new byte[14];
                port.readBytes(newData, 14); 
                System.out.println(String.format("Date : %s, Data: %s", 
                                    _formatter.format(new Date(System.currentTimeMillis())), 
                                    Double.parseDouble(new String(newData, StandardCharsets.UTF_8))));
            }
        });
    }

    /*
    Formats a carriage return to the parameter string command, sends it to the 
    serial port, waits for incoming data and reads the buffer
     */
    protected String GetCommandResponse(String cmd) throws InterruptedException{
        if (_thisPort.isOpen()){
            try{
                SerialPortSlowWrite(cmd + "\r", 10);
                Thread.sleep(250);
                byte[] buffer = new byte[_thisPort.bytesAvailable()-1];
                _thisPort.readBytes(buffer, buffer.length-1);
                _thisPort.flushIOBuffers();
                return new String(buffer, StandardCharsets.UTF_8);
            }
            catch (Exception ex){
                System.out.println(ex);
                return "";
            }
        }
        return "";
    }

    /*
    Adds a data listener to the serial port, tries to open the port
    and writes a command to receive continuous data from the sensor
     */
    public void StartReading() throws InterruptedException{
        AddSerialEventListener(_thisPort);
        TryOpen();
        SerialPortSlowWrite(_o0w0, 500);
    }

    /*
    Tries to open the port, sends a command to stop the incoming data,
    closes the port, and removes the data listener from the serial port
     */
    public void StopReading() throws InterruptedException{
        TryOpen();
        SerialPortSlowWrite("\r", 500);
        TryClose();
        _thisPort.removeDataListener();
    }

    /*
    Main method which starts upon running the program
     */
    public static void main(String[] args) throws Exception{

        Scanner scanner = new Scanner(System.in);

        //command will close the scanner object when the program is closed
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {scanner.close(); }));

        //Checks the available ports on the machine
        SerialPort[] ports = SerialPort.getCommPorts();

        //If there are no available ports, display so and close the program
        if (ports.length == 0){
            System.out.println("No available serial ports are on your machine");
            return;
        }

        String[] str_ports = new String[ports.length];

        for(int i=0; i<ports.length; i++){
            str_ports[i] = ports[i].getSystemPortName();
        }

        //Prints the available ports in the terminal and prompts the user to enter a port name
        System.out.println(String.format("Availble Ports : %s", Arrays.toString(str_ports)));
        System.out.println("Choose a port:");

        //Receive the inputted port typed into the terminal from the user
        String port = scanner.nextLine();

        //Check if the port is a valid available port
        int count = 0;
        for (int i=0; i<ports.length; i++){
            if (port.equals(str_ports[i])){
                count += 1;
            }
        }
        if (count == 0){
            throw new Exception("Please choose a valid serial port...");
        }

        //create a new sensor object and print its traits
        LoadstarSensor sensor = new LoadstarSensor(port,9600);
        System.out.println(String.format("ID: %s, Port: %s, Baudrate: %s, Type: %s, Units: %s",
                            sensor._id,
                            sensor._portName,
                            sensor._baud,
                            sensor._type,
                            sensor._units));

        //read continuous data from the sensor for 10 seconds
        sensor.StartReading();
        Thread.sleep(10000);
        sensor.StopReading();
    }
}

