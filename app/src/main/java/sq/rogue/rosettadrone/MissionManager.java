/**
 * This class implements a MAVLink Waypoint Missions Manager which is basically a
 * MAVLink Flight Controller that uses VirtualSticks and runs on top of the DJI Mobile SDK.
 *
 * Supports: DJI MINI, DJI MINI SE, DJI MINI 2, DJI MINI 2 SE and all DJI drones (that support VirtualStick Mode).
 *
 * Author: Christopher Pereira (rosetta@imatronix.com)
 */

package sq.rogue.rosettadrone;

import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_LAND;

import android.util.Log;

import com.MAVLink.common.msg_mission_item_int;
import com.MAVLink.enums.MAV_CMD;

import java.util.ArrayList;

public class MissionManager {
	public boolean photoTaken = false;
	public boolean requestOnePicture = false;

	// wpVariables are 1 WayPoint specific
	private int wpDelay;
	public boolean wpWaiting = false;
	public float wpAirSpeed = 0.5f;

	public boolean lookAtPOI; // 'true' if set by a waypoint. Once set, it remains set, until unset by another WayPoint with (0,0) coords.
	float baseAlt;
	private ArrayList<msg_mission_item_int> missionItems = null;

	public boolean paused = false;
	final private DroneModel droneModel;

	private final String TAG = MissionManager.class.getSimpleName();

	// Status fields modified by other threads
	private boolean statusTakeOffReady;
	private boolean statusLanded;

	MissionManager(DroneModel droneModel) {
		this.droneModel = droneModel;
	}

	public boolean isAutoMode() {
		return waypointHandlerThread != null && !paused;
	}

	// Points to POI or to next waypoint if lookAtPOI = false
	double poiLat = 0;
	double poiLng = 0;

	class MissionHandlerThread extends Thread {
		@Override
		public void run() {
			try {
				task();
				Log.d(TAG, "Mission finished");

			} catch(InterruptedException e) {
				Log.d(TAG, "Mission canceled");

			} finally {
				resetFlags();

				// CHANGED: It doesn't make sense to pause a finished mission. TODO: Mimic other MAVLink implementations.
				// droneModel.disableAllActions();
				paused = false;
				waypointHandlerThread = null;
			}
		}

		void task() throws InterruptedException {
			if(missionItems == null || missionItems.size() == 0) {
				// stopMission(); // Don't! stopMission() will try to terminate and join itself. Too insane.
				return;
			}

			baseAlt = 0;
			lookAtPOI = false;

			if(true) {
				int wpNum = 0;
				String info = "";
				for (msg_mission_item_int m : missionItems) {
					info += (++wpNum) + ": cmd = " + m.command + "\n";
				}
				Log.d(TAG, "Waypoint list:\n" + info);
			}

			int wpNum = 0;
			for (msg_mission_item_int m : missionItems) {
				handlePause();
				resetFlags();
				int seq = wpNum;
				wpNum++;
				resetCommandStatus();

				Log.i(TAG, "Started Waypoint #" + wpNum + ": " + m.command);

				switch (m.command) {

					case MAV_CMD.MAV_CMD_NAV_TAKEOFF:
						droneModel.doTakeOff(m.z, false); // TODO: Prevent send_command_ack()
						waitTakeOffReady();
						waitReachLocation();
						break;

					case MAV_CMD.MAV_CMD_NAV_WAYPOINT:
						if(wpNum == 1) {
							// First item sent by QGC is the "Mission Start" item for setting the mission altitude
							droneModel.mission_alt = baseAlt = m.z;
							continue;
						}

						wpDelay = (int) m.param1;

						flyTo(m.x / 10000000.0, m.y / 10000000.0, getAbsAltitude(m));
						waitReachLocation();
						break;

					case MAV_CMD.MAV_CMD_DO_SET_ROI:
						if(m.x == 0 && m.y == 0) {
							// Reset Poi
							lookAtPOI = false;
						} else {
							lookAtPOI = true;
							poiLat = m.x / 10000000.0;
							poiLng = m.y / 10000000.0;
						}
						break;

					case MAV_CMD.MAV_CMD_IMAGE_START_CAPTURE:
						// TODO: picturesInterval = m.param2;
						// TODO: picturesCount = m.param3;
						requestOnePicture = true; // TODO: Use requestMultiplePictures = true;
						break;

					case MAV_CMD.MAV_CMD_IMAGE_STOP_CAPTURE:
						// TODO: picturesInterval = m.param2;
						// TODO: picturesCount = m.param3;
						requestOnePicture = false; // TODO: Use requestMultiplePictures = false;
						break;

					case MAV_CMD.MAV_CMD_DO_SET_CAM_TRIGG_DIST:
						// TODO: cameraTriggerDistanceInterval = (m.param1); // in meters
						requestOnePicture = (m.param3 == 1);
						photoTaken = false;
						break;

					case MAV_CMD.MAV_CMD_NAV_DELAY:
						if(m.param1 == -1) {
							// TODO:
						} else {
							sleep((int) (1000 * m.param1));
						}
						break;

					case MAV_CMD.MAV_CMD_VIDEO_START_CAPTURE:
						droneModel.startRecordingVideo(false);
						break;

					case MAV_CMD.MAV_CMD_VIDEO_STOP_CAPTURE:
						droneModel.stopRecordingVideo(false);
						break;

					case MAV_CMD.MAV_CMD_DO_CHANGE_SPEED:
						if(m.param1 == 0) {
							wpAirSpeed = m.param2;
						} else {
							// TODO:
						}
						break;

					case MAV_CMD.MAV_CMD_NAV_RETURN_TO_LAUNCH:
						// droneModel.do_go_home();
						flyTo(droneModel.takeOffLocation[0], droneModel.takeOffLocation[1], droneModel.getGoHomeHeight());
						waitReachLocation();
						break;

					case MAV_CMD_NAV_LAND:
						droneModel.doLand();
						waitLanded();
						break;

					case MAV_CMD.MAV_CMD_DO_MOUNT_CONTROL:
					case MAV_CMD.MAV_CMD_CONDITION_YAW:
					case MAV_CMD.MAV_CMD_DO_DIGICAM_CONTROL:
					case MAV_CMD.MAV_CMD_SET_CAMERA_ZOOM:
					case MAV_CMD.MAV_CMD_SET_CAMERA_FOCUS:
					default:
						Log.e(TAG, "Mission Command not supported " + m.command);
						break;
				}

				// Make sure we take a photo if requested once
				Log.i(TAG, "Completed Waypoint #" + wpNum + ": " + m.command);

				droneModel.send_mission_item_reached(seq);

				takePhotos();
			}

			for(int s = 0; s < wpDelay; s++) {
				sleep(1000);
				handlePause();
			}
		}

		private void handlePause() throws InterruptedException {
			while(paused) {
				sleep(100); // TODO: Use wait() and notify()
			}
		}

		// Wait Methods

		int counter = 0;
		void wait(String reason) throws InterruptedException {
			sleep(100);
			handlePause();
			if(counter > 20) {
				Log.i(TAG, "Waiting " + reason + "...");
				counter = 0;
			}
		}

		void waitReachLocation() throws InterruptedException {
			// Wait until reaching destination
			while (droneModel.inMotion()) {
				wait("to reach destination");
			}
		}

		void waitTakeOffReady() throws InterruptedException {
			// Wait until reaching destination
			while (!statusTakeOffReady) {
				wait("take off");
			}
		}

		void waitLanded() throws InterruptedException {
			while (!statusLanded) {
				wait("landing");
			}
		}
	}

	// TODO: Use MAVlink-Waypoints instead of DJI-Waypoints. We need to support MAV_CMD_IMAGE_STOP_CAPTURE (not supported by DJI)
	void setMission(ArrayList<msg_mission_item_int> mission) {
		missionItems = mission;
	}

	void startMission() {
		synchronized (this) {
			paused = false;

			if (waypointHandlerThread != null) {
				Log.e(TAG, "BUG: Mission was already running. Interrupting current thread and starting a new one...Please use missionManager.isActive()");
				stopMission();
			}

			if (waypointHandlerThread == null) {
				Log.d(TAG, "Starting thread");
				waypointHandlerThread = new MissionHandlerThread();
				waypointHandlerThread.start();
			}
		}
	}

	/**
	 * @return true if mission started or paused.
	 */
	boolean isActive() {
		return waypointHandlerThread != null && !paused;
	}

	void stopMission() {
		synchronized (this) {
			if (waypointHandlerThread != null) {
				Log.d(TAG, "Interrupting thread - started");
				waypointHandlerThread.interrupt();
				try {
					waypointHandlerThread.join();
					Log.d(TAG, "Interrupting thread - finished");

				} catch(Exception e) {
					Log.d(TAG, "Interrupting thread - error");
				}
			}
			paused = false;
			resetFlags();
		}
	}

	synchronized void resumeMission() {
		synchronized (this) {
			if (missionItems == null) return;
			if (waypointHandlerThread == null) {
				Log.e(TAG, "BUG: waypointHandlerThread == null in resumeMission() => startMission()");
				startMission();
			} else {
				paused = false;
			}
		}
	}

	void pauseMission() {
		paused = true;
	}

	void flyTo(double targetLatitude, double targetLongitude, double targetAltitude) {
		flyTo(targetLatitude, targetLongitude, targetAltitude, wpAirSpeed);
	}

	void flyTo(double targetLatitude, double targetLongitude, double targetAltitude, double targetSpeed) {
		droneModel.flyTo(targetLatitude, targetLongitude, targetAltitude);
		droneModel.motion.yawDirection = lookAtPOI ? YawDirection.POI : YawDirection.DEST;
		droneModel.motion.speed = targetSpeed;
	}

	float getAbsAltitude(msg_mission_item_int m) {
		if (m.frame == 0) {
			// Absolute altitude
			return m.z;

		} else if(m.frame == 3) {
			// Relative altitude
			return m.z - baseAlt;

		} else {
			Log.e(TAG, "Unsupported reference frame " + m.frame);
			return 0;
		}
	}

	private Thread waypointHandlerThread = null;

	void resetCommandStatus() {
		// Reset all status flags
		statusTakeOffReady = false;
		statusLanded= false;
	}

	// Reset flags at the beginning of the next waypoint
	void resetFlags() {
		// Properties corresponding to the current
		wpDelay = 0;
		requestOnePicture = false;
		wpWaiting = false;
	}

	// Take one or more pictures. Is called during movement and when waypoint is reached.
	void takePhotos() {
		if (requestOnePicture && !photoTaken && !droneModel.inMotion()) {
			droneModel.takePhoto(false);
			photoTaken = true;
		}
	}

	// --- Methods for setting status from DroneModel ---

	public void setTakeOffStatus(boolean ready) {
		statusTakeOffReady = ready;
	}

	public void setLandedStatus(boolean ready) {
		statusLanded = ready;
	}
}
