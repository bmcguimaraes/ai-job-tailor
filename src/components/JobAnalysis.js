import React, { useState } from 'react';
import { AlertCircle, Loader } from 'lucide-react';
import './JobAnalysis.css';

function JobAnalysis({ onAnalyze, loading, currentResume }) {
  const [vacancyUrl, setVacancyUrl] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!vacancyUrl.trim()) {
      setError('Please enter a valid URL');
      return;
    }
    try {
      new URL(vacancyUrl);
      setError('');
      onAnalyze(vacancyUrl);
    } catch {
      setError('Please enter a valid URL');
    }
  };

  return (
    <div className="job-analysis">
      <div className="step-header">
        <div className="step-number">2</div>
        <div>
          <h2>Paste Job URL</h2>
          <p>LinkedIn, Indeed, or any job posting link</p>
        </div>
      </div>

      {currentResume && (
        <div className="current-resume-badge">
          ✓ {currentResume.filename} ready to analyze
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="vacancyUrl">Job Posting URL</label>
          <div className="input-wrapper">
            <input
              id="vacancyUrl"
              type="text"
              placeholder="https://linkedin.com/jobs/..."
              value={vacancyUrl}
              onChange={(e) => {
                setVacancyUrl(e.target.value);
                setError('');
              }}
              disabled={loading}
              className={error ? 'input-error' : ''}
            />
            {loading && <Loader className="input-loader" />}
          </div>
          {error && <p className="input-error-text">{error}</p>}
        </div>

        <button
          type="submit"
          className="btn btn-primary"
          disabled={loading || !vacancyUrl.trim()}
        >
          {loading ? (
            <>
              <Loader size={18} style={{ marginRight: '0.5rem' }} />
              Analyzing...
            </>
          ) : (
            'Analyze Job'
          )}
        </button>
      </form>

      <div className="help-section">
        <AlertCircle size={18} />
        <div>
          <h4>Can't scrape the URL?</h4>
          <p>If the page is blocked or not recognized, you can manually paste the job description instead. Just copy the text from the job posting.</p>
        </div>
      </div>
    </div>
  );
}

export default JobAnalysis;
