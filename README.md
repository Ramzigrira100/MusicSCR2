# X-Ray Mirror (Android + Web)

**Created by: Hashem Al-Ghaili**

See the skull, brain, eyes, muscles, and hand bones tracked live in 3D through your front or rear camera.

## Android Features
- **Full-Body X-Ray Experience**: Skull (cranium + animated jaw), Brain, Facial Muscles, Eyes, Full-Body Skeleton (cervical/thoracic/lumbar spine, ribcage, sternum, clavicles, scapulae, pelvis, humerus, radius & ulna, femur, tibia & fibula), Pulsing Heart & Lungs, Dual Hand Bones, Glowing Body & Head Outline, Mixed Reality feathering & reveal cut modes (`whole`, `left`, `right`, `top`, `bottom`, `scan`, `lens`), Anatomical Labels, Live Telemetry, and Fit Calibration sliders.
- **Smooth Mobile Performance**: Staggered tracker scheduling, 60fps pose/hand/head interpolation, half-res filter-free X-Ray veil rendering, and Smooth / HD quality toggle.
- **Front & Rear Camera Support**: Choose front (mirrored) or rear (true unmirrored orientation) camera before starting or toggle anytime via the floating flip button or Camera settings panel.
- **Native Gallery Saving**: Captured PNG photos and MP4/WebM videos save directly to `Pictures/X-Ray Mirror` and `Movies/X-Ray Mirror` via Android `MediaStore`.
- **Code Assist Ready**: Standard Groovy Gradle + pure Java (`MainActivity.java`) configured for building directly on your Android phone with **Code Assist** or on desktop with **Android Studio**.

## Building on a Phone with Code Assist
1. Copy or clone this repository folder onto your phone's storage (or import via Git in Code Assist).
2. Open **Code Assist** and select **Open Project**, choosing the root folder containing `settings.gradle` and `build.gradle`.
3. Ensure an internet connection is available on first build so Code Assist can resolve `androidx.appcompat`, `androidx.webkit`, and `material`.
4. Tap **Run / Build APK** to compile and install directly on your phone.

## Credits
Anatomy models: BodyParts3D, © The Database Center for Life Science, licensed under CC Attribution-Share Alike 2.1 Japan. Models were simplified and re-aligned.

## Tracking reliability improvements
- Full-body rendering now requires a confident torso detection (both shoulders and hips) with plausible shoulder/hip proportions and torso geometry. This reduces false full-body skeletons when the camera is close to a hand.
- Two consecutive valid torso detections are required before the body rig appears; brief missed detections are allowed to fade out through the existing timeout to reduce flicker.
- Switching cameras clears stale hand/body landmark buffers so the previous camera pose cannot briefly remain over the new camera feed.
- Camera startup now tries an exact facing-mode request followed by a non-exact facing-mode fallback before device enumeration.

These are tracking and stability improvements, not medical imaging. Real-world alignment and performance still require testing on the target Android phone under different lighting, distances, and body poses.
