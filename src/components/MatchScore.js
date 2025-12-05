import React from 'react';
import { TrendingUp } from 'lucide-react';
import './MatchScore.css';

function MatchScore({ score, jobData }) {
  const getScoreBgColor = (score) => {
    if (score >= 75) return 'score-good';
    if (score >= 50) return 'score-moderate';
    return 'score-poor';
  };

  const getScoreLabel = (score) => {
    if (score >= 75) return 'Good Match';
    if (score >= 50) return 'Moderate Match';
    return 'Poor Match';
  };

  const details = score.details || {};

  return (
    <div className="match-score">
      <div className="step-header">
        <div className="step-number">3</div>
        <div>
          <h2>Match Analysis</h2>
          <p>How well your resume matches this job</p>
        </div>
      </div>

      {jobData && (
        <div className="job-info">
          <div>
            <h3>{jobData.company}</h3>
            <p>{jobData.position}</p>
          </div>
          {jobData.salary && <span className="salary">{jobData.salary}</span>}
        </div>
      )}

      <div className={`score-box ${getScoreBgColor(score.score)}`}>
        <div className="score-value">{score.score}</div>
        <div className="score-label">{getScoreLabel(score.score)}</div>
        <div className="score-max">/100</div>
      </div>

      <div className="score-breakdown">
        <h3>Score Breakdown</h3>
        <div className="breakdown-items">
          <div className="breakdown-item">
            <span>Soft Skills</span>
            <div className="score-bar">
              <div
                className="score-fill"
                style={{ width: `${details.softSkillsScore || 0}%` }}
              />
            </div>
            <span className="score-percent">{details.softSkillsScore || 0}%</span>
          </div>

          <div className="breakdown-item">
            <span>Technical Skills</span>
            <div className="score-bar">
              <div
                className="score-fill"
                style={{ width: `${details.technicalSkillsScore || 0}%` }}
              />
            </div>
            <span className="score-percent">{details.technicalSkillsScore || 0}%</span>
          </div>

          <div className="breakdown-item">
            <span>Job Functions</span>
            <div className="score-bar">
              <div
                className="score-fill"
                style={{ width: `${details.jobFunctionsScore || 0}%` }}
              />
            </div>
            <span className="score-percent">{details.jobFunctionsScore || 0}%</span>
          </div>

          <div className="breakdown-item">
            <span>Role & Experience</span>
            <div className="score-bar">
              <div
                className="score-fill"
                style={{ width: `${details.roleExperienceScore || 0}%` }}
              />
            </div>
            <span className="score-percent">{details.roleExperienceScore || 0}%</span>
          </div>

          <div className="breakdown-item">
            <span>Keywords</span>
            <div className="score-bar">
              <div
                className="score-fill"
                style={{ width: `${details.keywordScore || 0}%` }}
              />
            </div>
            <span className="score-percent">{details.keywordScore || 0}%</span>
          </div>
        </div>
      </div>

      {score.missingKeywords && score.missingKeywords.length > 0 && (
        <div className="missing-keywords">
          <h3>Missing Keywords</h3>
          <div className="keywords-list">
            {score.missingKeywords.map((kw, idx) => (
              <span key={idx} className="keyword-tag">
                {kw}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default MatchScore;
