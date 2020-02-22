package frc.robot;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.can.VictorSPX;
import com.revrobotics.*;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;
import edu.wpi.first.wpilibj.PowerDistributionPanel;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.Compressor;
import edu.wpi.first.wpilibj.Solenoid;

public class shooter {
    public double kP, kI, kD, kIz, kFF, kMaxOutput, kMinOutput, maxRPM;
    private boolean inFault = false;
    public Compressor compressor;
    public Solenoid hood1;
    public Solenoid hood2;
    public CANSparkMax m_motor;
    public CANSparkMax m_mator;
    public VictorSPX m_feeder;
    public CANEncoder m_encoder;
    public CANEncoder m_encoder2;
    public CANPIDController m_pidController;
    public double PIDTarget;
    public boolean Ready;
    public int Stabalizer;
    public double IAccum;
    public double rpm;
    public double rpm2;
    public PowerDistributionPanel powerPannel;
    public boolean lowVoltage = false;
    public int lowVoltageLockoutCnt = 0;
    public boolean lowVoltageLockout = false;
    private boolean shootin;

    public shooter() {
        compressor = new Compressor(Calibrations.CAN_ID.pcm);
        compressor.setClosedLoopControl(true);
        hood1 = new Solenoid(6);
        hood2 = new Solenoid(7);
        powerPannel = new PowerDistributionPanel();
        kP = Calibrations.shooter_PID.kP;
        kI = Calibrations.shooter_PID.kI;
        kD = Calibrations.shooter_PID.kD;
        kIz = Calibrations.shooter_PID.kIz;
        kFF = Calibrations.shooter_PID.kFF;
        kMaxOutput = Calibrations.shooter_PID.kMaxOutput;
        kMinOutput = Calibrations.shooter_PID.kMinOutput;
        m_motor = new CANSparkMax(Calibrations.CAN_ID.shooter1, MotorType.kBrushless);
        m_mator = new CANSparkMax(Calibrations.CAN_ID.shooter2, MotorType.kBrushless);
        m_feeder = new VictorSPX(Calibrations.CAN_ID.indexer);
        m_feeder.setInverted(true);
        m_encoder = m_motor.getEncoder();
        m_encoder2 = m_mator.getEncoder();
        m_pidController = m_motor.getPIDController();
        m_pidController.setFeedbackDevice(m_encoder);
        m_pidController.setP(kP);
        m_pidController.setI(kI);
        m_pidController.setD(kD);
        m_pidController.setIZone(kIz);
        m_pidController.setFF(kFF);
        m_pidController.setOutputRange(kMinOutput, kMaxOutput);
        m_motor.setInverted(false);
        m_mator.follow(m_motor, true);
    }

    public void manual(double pwr){
        m_pidController.setReference(pwr, ControlType.kDutyCycle);
        PIDTarget = 0;
    }


    public boolean isInFault() {
        if(lowVoltageLockout || lowVoltage){
            inFault = true;
        }else{
            inFault = false;
        }
        return inFault;
    }

    public void stopPIDMotor() {
        m_pidController.setReference(0, ControlType.kDutyCycle);
        PIDTarget = 0;
        m_pidController.setOutputRange(0, kMaxOutput);
    }
    
    public void PIDmotor(double rpm){ 
        rpm = rpm * 24.0/36.0;
        if (lowVoltage == false){      
            m_pidController.setOutputRange(kMinOutput, kMaxOutput);
            PIDTarget = rpm;
            m_pidController.setReference(rpm, ControlType.kVelocity,0);
        }else{
            this.stopPIDMotor();
        }
    }

    public void PowerCheck(){
        if(powerPannel.getVoltage() < 8.25 && lowVoltage == false){
            lowVoltage = true;
            lowVoltageLockoutCnt++;
        }else if (powerPannel.getVoltage() > 9.25 && lowVoltage == true && lowVoltageLockout == false){
            lowVoltage = false;
        }
        if (lowVoltageLockoutCnt > 150 && lowVoltageLockout == false){
            lowVoltageLockout = false;
        }        
    }

    public void LockoutReset(){
        lowVoltageLockout = false;
        lowVoltageLockoutCnt = 0;
    }

    public boolean isShooterStable(){        
        return Ready;
    }

    public void indexPower(double pwr){
        m_feeder.set(ControlMode.PercentOutput,pwr);
        if(pwr > 0){
            shootin = true;
        }else{
            shootin = false;
        }
    }

    public void read(){
        this.PowerCheck();
        rpm = Math.abs(m_encoder.getVelocity() * (36.0/24.0));
        rpm2 = Math.abs(m_encoder2.getVelocity() * (36.0/24.0));
        IAccum = m_pidController.getIAccum();
        if ((Math.abs(PIDTarget*(36.0/24.0)-rpm)) < 50){
            Stabalizer++;
        }else{
            Stabalizer = 0;
        }
        if(Stabalizer > 10){
            Ready = true;
        }else{
            Ready = false;
        }
    }
    public enum HoodPosition{
        goingUnder,
        trench,
        wallShot,
        autoshot
    }
    public void setHood(HoodPosition pos){
        switch(pos){
            case goingUnder: //trench
                hood1.set(false); //short extened
                hood2.set(true); //long extented
            break;
            case trench:
                hood1.set(true); //short 
                hood2.set(false); //long
            break;
            case wallShot:
                hood1.set(false); //short 
                hood2.set(true); //long
            break;
            case autoshot:
                hood1.set(false); //short 
                hood2.set(false); //long
            break;
        }
    }

    public void updateStatus(){
        int status = 0;
        if(lowVoltage){
            status = 0;
        }else if(Ready && PIDTarget != 0){
            status = 3;
        }else if(PIDTarget != 0){
            status = 2;
        }else{
            status = 1;
        }
        SmartDashboard.putNumber("ShooterOutputStatus", status);
    }

    public void Display(){
        SmartDashboard.putNumber("I accumlator", IAccum);
        SmartDashboard.putNumber("Amp Draw",m_motor.getOutputCurrent());
        SmartDashboard.putBoolean("Voltage Stopped", lowVoltage);
        SmartDashboard.putBoolean("Voltage Lockout", lowVoltageLockout);
        SmartDashboard.putNumber("Encoder Position", m_encoder.getPosition());
        SmartDashboard.putNumber("Encoder Velocity", rpm);
        SmartDashboard.putNumber("Encoder Velocity2", rpm2);
        SmartDashboard.putNumber("Combined Velocity", (rpm+rpm2)/2);
        SmartDashboard.putNumber("Encoder Velocity Difference", rpm - rpm2);
        SmartDashboard.putNumber("PID Error",(PIDTarget*(36.0/24.0))-rpm);
        SmartDashboard.putNumber("OutPut Power", m_motor.getAppliedOutput());
        SmartDashboard.putBoolean("Ready?", Ready);
    }

}    
