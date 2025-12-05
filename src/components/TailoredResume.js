import React, { useState } from 'react';
import { Loader } from 'lucide-react';
import './TailoredResume.css';

function TailoredResume({ onGenerate, loading, selectedCount }) {
  const [maxDeviation, setMaxDeviation] = useState(40);

  const handleGenerate = () => {
    onGenerate(maxDeviation);
  };

  return (
    <div className="tailored-resume">
      <div className="step-header">
        <div className="step-number">5</div>
        <div>
          <h2>Generate Tailored Resume</h2>
          <p>Create a resume customized for this job</p>
        </div>
      </div>

      <div className="deviation-section">
        <h3>Maximum Deviation</h3>
        <p>How much can we rewrite your resume? (20% = minimal, 60% = significant changes)</p>

        <div className="slider-container">
          <input
            type="range"
            min="20"
            max="60"
            value={maxDeviation}
            onChange={(e) => setMaxDeviation(parseInt(e.target.value))}
            className="slider"
            disabled={loading}
          />
          <div className="slider-labels">
            <span>20%</span>
            <span className="slider-value">{maxDeviation}%</span>
            <span>60%</span>
          </div>
        </div>

        <p className="deviation-hint">
          {maxDeviation <= 30
            ? '🔒 Conservative: Small rewording, mostly keeping original'
            : maxDeviation <= 45
            ? '⚖️ Balanced: Good mix of rewording and adding new content'
            : '✨ Aggressive: Significant improvements, new bullets added'}
        </p>
      </div>

      <div className="generate-section">
        <p className="summary">
          Ready to tailor your resume using <strong>{selectedCount} improvements</strong>
        </p>
        <button
          onClick={handleGenerate}
          className="btn btn-primary"
          disabled={loading}
        >
          {loading ? (
            <>
              <Loader size={18} style={{ marginRight: '0.5rem' }} />
              Generating...
            </>
          ) : (
            'Generate DOCX'
          )}
        </button>
      </div>

      <div className="info-box">
        <h4>What happens next?</h4>
        <ol>
          <li>We'll generate a Word document with your tailored resume</li>
          <li>You can download and review it in Pages or Word</li>
          <li>Edit it if you want (we keep all facts accurate)</li>
          <li>When ready, come back and approve it</li>
        </ol>
      </div>
    </div>
  );
}

export default TailoredResume;
