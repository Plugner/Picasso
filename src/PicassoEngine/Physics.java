package PicassoEngine;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Physics implements Runnable {
	private Scene scene;
	private Timer timer;
	
	public Physics(Scene scene) {
		this.scene = scene;
		this.timer = new Timer();
	}
	
	public void run() {
		scene.callFixedUpdate();
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				physicsStep();
			}
		}, 0, 20);
	}
	
	public void physicsStep() {
		RigidBody[] rigidBodies = scene.getRigidBodies();
		Model[] colliders = scene.getColliders();
		
		// Act on each rigidbody
		for (RigidBody item : rigidBodies) {
			// Apply constant forces
			Vector3 forcesBeforeCollision = new Vector3();
			// Scene forces (gravity)
			forcesBeforeCollision.add(scene.getGravity());
			// Object forces
			for (Vector3 force : item.getForces()) {
				forcesBeforeCollision.add(force);
			}
			
			Vector3[] collisionPoints = worldCollision(item.getPosition(), item.getRadius());
			Vector3 collisionForces = new Vector3();
			for (Vector3 collisionPoint : collisionPoints) {
				if (collisionPoint != null) {
					// Collision movement unit vector
					Vector3 actionVector = new Vector3(collisionPoint.x - item.getPosition().x, collisionPoint.y - item.getPosition().y, collisionPoint.z - item.getPosition().z);
					actionVector.normalize();
					
					// Collision movement unit vector reflected on face of collision
					Vector3 reflectionVector = actionVector.getReflection(actionVector);
					
					// Add collision to the total collision forces
					collisionForces.add(reflectionVector.getProduct(forcesBeforeCollision).getScaled(-1));
					
					// Reflect the velocity
					item.setVelocity(item.getVelocity().getReflection(actionVector).getScaled(0.81));
				}
			}
			
			// Collision detection
			// Determine collision forces
			// Add collision forces to constant forces
			Vector3 forceSum = new Vector3();
			forceSum.add(forcesBeforeCollision);
			forceSum.add(collisionForces);
			
			// Find acceleration from total forces
			Vector3 acceleration = forceSum.getScaled(1.0 / item.getMass());
			
			// Find velocity from acceleration and mass, adding it to the velocity of the object
			item.addVelocity(acceleration.getScaled(0.02 * item.getMass()));
			
			// Displace rigidbodies with velocity
			item.addPosition(item.getVelocity().getScaled(0.02));
		}
	}
	
	public Vector3[] worldCollision(Vector3 ball, double radius) {
		ArrayList<Vector3> collisionPoints = new ArrayList<Vector3>();
		for (Model object : scene.getColliders()) {
			Vector3[] theseCollisions = objectCollision(object, ball, radius);
			for (Vector3 c : theseCollisions) {
				collisionPoints.add(c);
			}
		}
		
		Vector3[] result = new Vector3[collisionPoints.size()];
		for (int i = 0; i < collisionPoints.size(); i++) {
			result[i] = collisionPoints.get(i);
		}
		
		return result;
	}
	
	public Vector3[] objectCollision(Model object, Vector3 ball, double radius) {
		return new Vector3[]{planeCollision(object.getVertices(), ball, radius)};
	}
	
	public Vector3 planeCollision(Vector3[] planePoints, Vector3 ball, double radius) {
		// Find unit vector normal to the face
		Vector3 v1 = planePoints[2].getDifference(planePoints[1]);
		Vector3 v2 = planePoints[0].getDifference(planePoints[1]);
		Vector3 faceNormal = v1.getCrossProduct(v2);
		faceNormal.normalize();
		
		double plane = -1 * (faceNormal.x * planePoints[0].x + faceNormal.y * planePoints[0].y + faceNormal.z * planePoints[0].z);
		double distanceToPlane = faceNormal.x * ball.x + faceNormal.y * ball.y + faceNormal.z * ball.z + plane;
		
		if (distanceToPlane <= 0.5) {
			Vector3 ballToCollision = faceNormal.getScaled(-distanceToPlane);
			return ball.getSum(ballToCollision);
		} else {
			return null;
		}
	}
}
