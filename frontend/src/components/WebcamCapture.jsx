import { useEffect, useRef, useState } from 'react';
import './face.css';

// Shows the webcam and a button. When clicked, calls onCapture(blob) with a JPEG photo.
export default function WebcamCapture({ onCapture, buttonText = 'Take photo', busy = false }) {
  const videoRef = useRef(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let stream = null;
    let cancelled = false;

    navigator.mediaDevices
      .getUserMedia({ video: { width: 640, height: 480 }, audio: false })
      .then((s) => {
        if (cancelled) {
          s.getTracks().forEach((t) => t.stop());
          return;
        }
        stream = s;
        videoRef.current.srcObject = s;
        setReady(true);
      })
      .catch(() => setError('Could not open the camera. Check it is connected and allowed.'));

    return () => {
      cancelled = true;
      if (stream) stream.getTracks().forEach((t) => t.stop());
    };
  }, []);

  function snap() {
    const v = videoRef.current;
    if (!v || !v.videoWidth) return;
    const canvas = document.createElement('canvas');
    canvas.width = v.videoWidth;
    canvas.height = v.videoHeight;
    canvas.getContext('2d').drawImage(v, 0, 0);
    canvas.toBlob((blob) => blob && onCapture(blob), 'image/jpeg', 0.9);
  }

  if (error) return <div className="error">{error}</div>;

  return (
    <div className="cam">
      <video ref={videoRef} autoPlay playsInline muted />
      <button type="button" onClick={snap} disabled={!ready || busy}>
        {busy ? 'Please wait...' : buttonText}
      </button>
    </div>
  );
}
