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
#	while vehicle.is_armable!=True:
#		print("Waiting for vehicle to become armable.")
#		time.sleep(1)
    print("Vehicle is now armable")

    vehicle.mode = VehicleMode("GUIDED")

    while vehicle.mode!='GUIDED':
        print("Waiting for drone to enter GUIDED flight mode")
        time.sleep(1)
    print("Vehicle now in GUIDED MODE. Have fun!!")

    vehicle.armed = True
#	while vehicle.armed==False:
#		print("Waiting for vehicle to become armed.")
#		time.sleep(1)
    print("Look out! Virtual props are spinning!!")

    time.sleep(5)

    vehicle.simple_takeoff(targetHeight) ##meters

    while True:
        print("Current Altitude: %d"%vehicle.location.global_relative_frame.alt)
        if vehicle.location.global_relative_frame.alt>=.95: #*targetHeight:
            break
        time.sleep(1)
#
#	while vehicle.location.global_relative_frame.alt <= targetHeight:
#		send_local_ned_velocity(0,0,-5)
#		time.sleep(1)
#		print("Current Altitude: %d"%vehicle.location.global_relative_frame.alt)

    print("Target altitude reached!!")

    # Hover for 10 seconds
    time.sleep(5)

    print("Now let's land")
    vehicle.mode = VehicleMode("LAND")
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
        0,                                      # time_boot_ms (not used)
        0, 0,                                   # target system, target component
        mavutil.mavlink.MAV_FRAME_LOCAL_NED,    # frame
        0b0000111111000111,                     # type_mask (only speeds enabled)
        0, 0, 0,                                # x, y, z positions (not used)
        vx, vy, vz,                             # x, y, z velocity in m/s
        0, 0, 0,                                # x, y, z acceleration (not supported yet, ignored in GCS_Mavlink)
        0, 0)                                   # yaw, yaw_rate (not supported yet, ignored in GCS_Mavlink)
    vehicle.send_mavlink(msg)
    vehicle.flush()

##########MAIN EXECUTABLE###########

vehicle = connectMyCopter()
arm_and_takeoff(10)
time.sleep(2)
#
#counter=0
#while counter<5:
#	send_local_ned_velocity(5,0,0)
#	time.sleep(1)
#	print("Moving NORTH relative to front of drone")
#	counter=counter+1
#
#time.sleep(2)
#
#counter=0
#while counter<5:
#	send_local_ned_velocity(0,-5,0)
#	time.sleep(1)
#	print("Moving WEST relative to front of drone")
#	counter=counter+1
#
#
#time.sleep(2)
#
#while counter<5:
#	send_global_ned_velocity(5,0,0)
#	time.sleep(1)
#	print("Moving TRUE NORTH relative to front of drone")
#	counter=counter+1
#
#time.sleep(2)
#
#counter=0
#while counter<5:
#	send_global_ned_velocity(0,-5,0)
#	time.sleep(1)
#	print("Moving TRUE WEST relative to front of drone")
#	counter=counter+1
#
##########UP AND DOWN############
#time.sleep(2)
#
#counter=0
#while counter<5:
#	send_local_ned_velocity(0,0,-5)
#	time.sleep(1)
#	print("Moving UP")
#	counter=counter+1
#
#time.sleep(2)
#
#counter=0
#while counter<5:
#	send_local_ned_velocity(0,0,5)
#	time.sleep(1)
#	print("Moving DOWN")
#	counter=counter+1
#
#
## Hover for 10 seconds
#time.sleep(5)
#
#print("Now let's land")
#vehicle.mode = VehicleMode("LAND")
#

