##########DEPENDENCIES#############

from dronekit import connect, VehicleMode,LocationGlobalRelative,APIException
import time
import socket
import exception
import math
import argparse
from pymavlink import mavutil
#########FUNCTIONS#################

def connectMyCopter():
    parser = argparse.ArgumentParser(description='commands')
    parser.add_argument('--connect')
    args = parser.parse_args()
    connection_string = args.connect
    if not connection_string:
        import dronekit_sitl
        sitl = dronekit_sitl.start_default()
        connection_string = sitl.connection_string()

    vehicle = connect(connection_string,wait_ready=True)
    return vehicle

def arm_and_takeoff(targetHeight):
    timeout = 0
    while vehicle.is_armable!=True and timeout < 5:
        print("Waiting for vehicle to become armable.")
        timeout = timeout +1
        time.sleep(1)

    print("Vehicle is now armable")
    vehicle.mode = VehicleMode("GUIDED")
    while vehicle.mode!='GUIDED':
        print("Waiting for drone to enter GUIDED flight mode")
        time.sleep(1)

    print("Vehicle now in GUIDED MODE. Have fun!!")
    vehicle.armed = True
    timeout = 0
    while vehicle.armed==False and timeout < 5:
        print("Waiting for vehicle to become armed.")
        timeout = timeout +1
        time.sleep(1)

    print("Look out! Virtual props are spinning!!")
    time.sleep(5)
    vehicle.simple_takeoff(targetHeight) ##meters
    while True:
        print("Current Altitude: %f"%vehicle.location.global_relative_frame.alt)
        if vehicle.location.global_relative_frame.alt>=.95: #*targetHeight:
            break
        time.sleep(1)

    while vehicle.location.global_relative_frame.alt <= targetHeight*.95:
        send_local_ned_velocity(0,0,-2)
        time.sleep(1)
        print("Current Altitude: %f"%vehicle.location.global_relative_frame.alt)

    send_local_ned_velocity(0,0,0)
    print("Target altitude reached!!")
    return None

##Send a velocity command with +x being the heading of the drone.
def send_local_ned_velocity(vx, vy, vz):
    msg = vehicle.message_factory.set_position_target_local_ned_encode(
        0,
        0, 0,
        mavutil.mavlink.MAV_FRAME_BODY_OFFSET_NED,
        0b0000111111000111,
        0, 0, 0,
        vx, vy, vz,
        0, 0, 0,
        0, 0)
    vehicle.send_mavlink(msg)
    vehicle.flush()

##Send a velocity command with +x being the heading of the drone.
def send_global_ned_velocity(vx, vy, vz):
    msg = vehicle.message_factory.set_position_target_local_ned_encode(
        0, # time_boot_ms (not used)
        0, 0, # target system, target component
        mavutil.mavlink.MAV_FRAME_LOCAL_NED, #frame
        0b0000111111000111, #type_mask (only speeds enabled)
        0, 0, 0, # x, y, z positions (not used)
        vx, vy, vz, # x, y, z velocity in m/s
        0, 0, 0, #x, y, z acceleration (not supported yet, ignored in GCS_Mavlink)
        0, 0) #yaw, yaw_rate (not supported yet, ignored in GCS_Mavlink)
    vehicle.send_mavlink(msg)
    vehicle.flush()

# Set servo output, values are in degrees
def set_servo(servo,val):
    msg = vehicle.message_factory.command_long_encode(
            0, # time_boot_ms (not used)
            0,  # target system, target component
            mavutil.mavlink.MAV_CMD_DO_SET_SERVO,
            0,
            servo, # RC channel...
            1500+(val*5.5), # RC value
            0, 0, 0, 0, 0)
    vehicle.send_mavlink(msg)
    vehicle.flush()

##########MAIN EXECUTABLE###########

vehicle = connectMyCopter()
arm_and_takeoff(10)

#------------------------------------------------------
# Do gimbal test...

set_servo(9,0.0)
set_servo(8,0.0)
time.sleep(2)
set_servo(8,-45.0)
time.sleep(5)
set_servo(8,45.0)
time.sleep(5)
set_servo(8,0.0)
time.sleep(5)
set_servo(9,-45.0)
time.sleep(5)
set_servo(9,-90.0)
time.sleep(6)
set_servo(9,20.0)
time.sleep(5)
set_servo(9,0.0)


counter=0
while counter<5:
	send_local_ned_velocity(2,0,0)
	time.sleep(1)
	print("Moving NORTH relative to front of drone")
	counter=counter+1

time.sleep(2)

counter=0
while counter<5:
	send_local_ned_velocity(0,-2,0)
	time.sleep(1)
	print("Moving WEST relative to front of drone")
	counter=counter+1

time.sleep(2)

counter=0
while counter<5:
	send_local_ned_velocity(-2,0,0)
	time.sleep(1)
	print("Moving NORTH relative to front of drone")
	counter=counter+1

time.sleep(2)

counter=0
while counter<5:
	send_local_ned_velocity(0,2,0)
	time.sleep(1)
	print("Moving WEST relative to front of drone")
	counter=counter+1

#------------------------------------------------------
time.sleep(2)

counter=0
while counter<5:
	send_global_ned_velocity(2,0,0)
	time.sleep(1)
	print("Moving TRUE NORTH relative to front of drone")
	counter=counter+1

time.sleep(2)

counter=0
while counter<5:
	send_global_ned_velocity(0,-2,0)
	time.sleep(1)
	print("Moving TRUE WEST relative to front of drone")
	counter=counter+1

time.sleep(2)

counter=0
while counter<5:
	send_global_ned_velocity(-2,0,0)
	time.sleep(1)
	print("Moving TRUE SOUTH relative to front of drone")
	counter=counter+1

time.sleep(2)

counter=0
while counter<5:
	send_global_ned_velocity(0,2,0)
	time.sleep(1)
	print("Moving TRUE EAST relative to front of drone")
	counter=counter+1

#------------------------------------------------------
#########UP AND DOWN############
time.sleep(2)

counter=0
while counter<5:
	send_local_ned_velocity(0,0,-2)
	time.sleep(1)
	print("Moving UP")
	counter=counter+1

time.sleep(2)

counter=0
while counter<5:
	send_local_ned_velocity(0,0,2)
	time.sleep(1)
	print("Moving DOWN")
	counter=counter+1


# Hover for 10 seconds
time.sleep(5)

print("Now let's land")
vehicle.mode = VehicleMode("LAND")
time.sleep(5)

print("Waiting for landing")
while vehicle.mode=='LAND':
    print("Waiting for vehicle to land.")
    print("Current Altitude: %f"%vehicle.location.global_relative_frame.alt)
    time.sleep(1)
