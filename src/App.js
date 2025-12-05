import React, { useState, useEffect } from 'react';
import axios from 'axios';
import ResumeUpload from './components/ResumeUpload';
import JobAnalysis from './components/JobAnalysis';
import MatchScore from './components/MatchScore';
import ImprovementsPreview from './components/ImprovementsPreview';
import TailoredResume from './components/TailoredResume';
import ApprovalStep from './components/ApprovalStep';
import './App.css';

const API_BASE = 'http://localhost:8080/api/resumes';

function App() {
  const [currentStep, setCurrentStep] = useState(1);
  const [resumeId, setResumeId] = useState(null);
  const [resumeData, setResumeData] = useState(null);
  const [jobData, setJobData] = useState(null);
  const [matchScore, setMatchScore] = useState(null);
  const [improvements, setImprovements] = useState(null);
  const [selectedImprovements, setSelectedImprovements] = useState([]);
  const [tailoredData, setTailoredData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Load resume if exists
  useEffect(() => {
    const savedResumeId = localStorage.getItem('resumeId');
    if (savedResumeId) {
      loadResume(parseInt(savedResumeId));
    }
  }, []);

  const loadResume = async (id) => {
    try {
      const response = await axios.get(`${API_BASE}/${id}`);
      setResumeData(response.data);
      setResumeId(id);
      localStorage.setItem('resumeId', id);
    } catch (err) {
      setError('Failed to load resume');
    }
  };

  const handleResumeUpload = async (file) => {
    setLoading(true);
    setError(null);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const response = await axios.post(`${API_BASE}/upload`, formData);
      setResumeData(response.data);
      setResumeId(response.data.id);
      localStorage.setItem('resumeId', response.data.id);
      setCurrentStep(2);
    } catch (err) {
      setError('Failed to upload resume. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleJobAnalysis = async (vacancyUrl) => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.post(
        `${API_BASE}/analyze/${resumeId}?vacancyUrl=${encodeURIComponent(vacancyUrl)}`
      );

      if (!response.data.success && response.data.message) {
        setError(response.data.message);
        return;
      }

      if (response.data.redFlag) {
        setError(`⛔ Red Flag: ${response.data.redFlagReason}. Not recommended for this role.`);
        return;
      }

      setJobData(response.data.jobExtraction);
      setMatchScore(response.data.matchingScore);
      setImprovements(response.data.improvements);
      setSelectedImprovements(
        response.data.improvements.improvements
          .slice(0, 3)
          .map((_, idx) => idx)
      );
      setCurrentStep(3);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to analyze job. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateTailored = async (maxDeviation) => {
    setLoading(true);
    setError(null);
    try {
      const selectedKeywords = selectedImprovements
        .flatMap(idx => improvements.improvements[idx].keywordsAdded)
        .filter((kw, idx, self) => self.indexOf(kw) === idx);

      const response = await axios.post(
        `${API_BASE}/tailor/${resumeId}`,
        {
          selectedKeywords,
          maxDeviationPercent: maxDeviation,
        }
      );

      setTailoredData(response.data);
      setCurrentStep(5);
    } catch (err) {
      setError('Failed to tailor resume. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async (docxBase64) => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.post(
        `${API_BASE}/approve/${resumeId}`,
        {
          company: jobData.company || 'Unknown',
          position: jobData.position || 'Unknown',
          docxBase64,
          improvements: improvements.improvements.filter((_, idx) =>
            selectedImprovements.includes(idx)
          ),
        }
      );

      if (response.data.success) {
        setCurrentStep(6);
      } else {
        setError('Failed to save resume');
      }
    } catch (err) {
      setError('Failed to approve and save. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const resetWorkflow = () => {
    setCurrentStep(2);
    setJobData(null);
    setMatchScore(null);
    setImprovements(null);
    setTailoredData(null);
    setError(null);
  };

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header-content">
          <h1>📄 AI Resume Tailor</h1>
          <p>Optimize your resume for every job</p>
        </div>
      </header>

      <main className="app-main">
        {error && (
          <div className={`error-banner ${error.includes('⛔') ? 'error-critical' : ''}`}>
            <p>{error}</p>
            <button onClick={() => setError(null)} className="error-close">✕</button>
          </div>
        )}

        <div className="steps-container">
          {/* Step 1: Upload Resume */}
          {(currentStep === 1 || !resumeId) && (
            <ResumeUpload
              onUpload={handleResumeUpload}
              loading={loading}
              existingResume={resumeData}
            />
          )}

          {/* Step 2: Job Analysis */}
          {currentStep >= 2 && resumeId && (
            <JobAnalysis
              onAnalyze={handleJobAnalysis}
              loading={loading}
              currentResume={resumeData}
            />
          )}

          {/* Step 3: Match Score */}
          {currentStep >= 3 && matchScore && (
            <MatchScore score={matchScore} jobData={jobData} />
          )}

          {/* Step 4: Improvements Preview */}
          {currentStep >= 4 && improvements && (
            <ImprovementsPreview
              improvements={improvements.improvements}
              selectedImprovements={selectedImprovements}
              onSelectionChange={setSelectedImprovements}
            />
          )}

          {/* Step 5: Tailored Resume */}
          {currentStep >= 5 && !tailoredData && (
            <TailoredResume
              onGenerate={handleGenerateTailored}
              loading={loading}
              selectedCount={selectedImprovements.length}
            />
          )}

          {/* Step 6: Download & Approve */}
          {currentStep >= 5 && tailoredData && (
            <ApprovalStep
              tailoredData={tailoredData}
              jobData={jobData}
              onApprove={handleApprove}
              loading={loading}
            />
          )}

          {/* Step 7: Success */}
          {currentStep === 6 && (
            <div className="success-container">
              <div className="success-box">
                <div className="success-icon">✅</div>
                <h2>Resume Saved Successfully!</h2>
                <p>Your tailored resume has been saved to:</p>
                <p className="success-path">/Users/brunoguimaraes/Documents/JA/</p>
                <button onClick={resetWorkflow} className="btn btn-primary">
                  Analyze Another Job
                </button>
              </div>
            </div>
          )}
        </div>
      </main>

      <footer className="app-footer">
        <p>💡 Tip: Review your resume in Pages or Word before applying</p>
      </footer>
    </div>
  );
}

export default App;
