import React, { useState } from 'react';
import { Download, CheckCircle2, Loader } from 'lucide-react';
import './ApprovalStep.css';

function ApprovalStep({ tailoredData, jobData, onApprove, loading }) {
  const [checklist, setChecklist] = useState({
    reviewed: false,
    truthful: false,
    ready: false,
  });

  const allChecked = checklist.reviewed && checklist.truthful && checklist.ready;

  const downloadDocx = () => {
    const byteCharacters = atob(tailoredData.docxBase64);
    const byteNumbers = new Array(byteCharacters.length);
    for (let i = 0; i < byteCharacters.length; i++) {
      byteNumbers[i] = byteCharacters.charCodeAt(i);
    }
    const byteArray = new Uint8Array(byteNumbers);
    const blob = new Blob([byteArray], { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = tailoredData.docxFileName;
    link.click();
    URL.revokeObjectURL(url);
  };

  const handleApprove = () => {
    if (allChecked) {
      onApprove(tailoredData.docxBase64);
    }
  };

  return (
    <div className="approval-step">
      <div className="step-header">
        <div className="step-number">6</div>
        <div>
          <h2>Review & Approve</h2>
          <p>Download and review your tailored resume</p>
        </div>
      </div>

      {jobData && (
        <div className="job-summary">
          <h3>For: {jobData.company} - {jobData.position}</h3>
          {jobData.url && (
            <a href={jobData.url} target="_blank" rel="noopener noreferrer" className="job-link">
              View Job Posting ↗
            </a>
          )}
        </div>
      )}

      <div className="download-section">
        <div className="deviation-info">
          <p><strong>Changes made:</strong> ~{tailoredData.deviationPercent}% of content rewritten</p>
        </div>
        
        <button
          onClick={downloadDocx}
          className="btn btn-primary"
          disabled={loading}
        >
          <Download size={18} style={{ marginRight: '0.5rem' }} />
          Download DOCX Resume
        </button>

        <p className="macos-info">
          💡 <strong>macOS Tip:</strong> Open with Pages (built-in), Word, or any Office app
        </p>
      </div>

      <div className="checklist-section">
        <h3>Before Approving</h3>
        <p>Please verify:</p>

        <div className="checklist">
          <label className="checklist-item">
            <input
              type="checkbox"
              checked={checklist.reviewed}
              onChange={(e) => setChecklist({ ...checklist, reviewed: e.target.checked })}
            />
            <CheckCircle2 size={20} />
            <span>I reviewed the resume in Pages/Word</span>
          </label>

          <label className="checklist-item">
            <input
              type="checkbox"
              checked={checklist.truthful}
              onChange={(e) => setChecklist({ ...checklist, truthful: e.target.checked })}
            />
            <CheckCircle2 size={20} />
            <span>All information is truthful and accurate</span>
          </label>

          <label className="checklist-item">
            <input
              type="checkbox"
              checked={checklist.ready}
              onChange={(e) => setChecklist({ ...checklist, ready: e.target.checked })}
            />
            <CheckCircle2 size={20} />
            <span>I'm ready to apply for this job</span>
          </label>
        </div>
      </div>

      <div className="approval-buttons">
        <button
          onClick={handleApprove}
          className="btn btn-primary"
          disabled={!allChecked || loading}
        >
          {loading ? (
            <>
              <Loader size={18} style={{ marginRight: '0.5rem' }} />
              Saving...
            </>
          ) : (
            <>
              <CheckCircle2 size={18} style={{ marginRight: '0.5rem' }} />
              Approve & Save Resume
            </>
          )}
        </button>
      </div>

      <div className="saved-to">
        <p>✅ Saved to: <code>/Users/brunoguimaraes/Documents/JA/</code></p>
      </div>
    </div>
  );
}

export default ApprovalStep;
