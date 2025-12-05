import React, { useRef, useState } from 'react';
import { Upload } from 'lucide-react';
import './ResumeUpload.css';

function ResumeUpload({ onUpload, loading, existingResume }) {
  const fileInputRef = useRef(null);
  const [dragActive, setDragActive] = useState(false);

  const handleDrag = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      onUpload(e.dataTransfer.files[0]);
    }
  };

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      onUpload(e.target.files[0]);
    }
  };

  return (
    <div className="resume-upload">
      <div className="step-header">
        <div className="step-number">1</div>
        <div>
          <h2>Upload Your Resume</h2>
          <p>PDF format • Once is enough, we'll reuse it</p>
        </div>
      </div>

      {existingResume ? (
        <div className="existing-resume">
          <div className="resume-info">
            <div className="resume-icon">📄</div>
            <div>
              <h3>{existingResume.filename}</h3>
              <p>Loaded • Ready to analyze new jobs</p>
            </div>
          </div>
          <button
            onClick={() => fileInputRef.current?.click()}
            className="btn btn-secondary btn-sm"
          >
            Upload New Resume
          </button>
        </div>
      ) : (
        <div
          className={`upload-area ${dragActive ? 'active' : ''}`}
          onDragEnter={handleDrag}
          onDragLeave={handleDrag}
          onDragOver={handleDrag}
          onDrop={handleDrop}
        >
          <Upload size={48} />
          <h3>Drag your resume here</h3>
          <p>or click to select</p>
          <p className="text-small">PDF format, max 10MB</p>
        </div>
      )}

      <input
        ref={fileInputRef}
        type="file"
        accept=".pdf"
        onChange={handleFileChange}
        style={{ display: 'none' }}
        disabled={loading}
      />

      {!existingResume && (
        <button
          onClick={() => fileInputRef.current?.click()}
          className="btn btn-primary"
          disabled={loading}
        >
          {loading ? 'Uploading...' : 'Choose Resume'}
        </button>
      )}
    </div>
  );
}

export default ResumeUpload;
