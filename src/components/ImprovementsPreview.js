import React from 'react';
import { CheckCircle2, TrendingUp } from 'lucide-react';
import './ImprovementsPreview.css';

function ImprovementsPreview({
  improvements,
  selectedImprovements,
  onSelectionChange,
}) {
  const toggleImprovement = (idx) => {
    if (selectedImprovements.includes(idx)) {
      onSelectionChange(selectedImprovements.filter((i) => i !== idx));
    } else {
      onSelectionChange([...selectedImprovements, idx]);
    }
  };

  const totalImpact = improvements
    .filter((_, idx) => selectedImprovements.includes(idx))
    .reduce((sum, imp) => sum + (imp.impactScoreIncrease || 0), 0);

  return (
    <div className="improvements-preview">
      <div className="step-header">
        <div className="step-number">4</div>
        <div>
          <h2>Select Improvements</h2>
          <p>Choose which improvements to apply to your resume</p>
        </div>
      </div>

      <div className="projected-score">
        <TrendingUp size={20} />
        <span>With selected improvements: <strong>+{totalImpact} points</strong></span>
      </div>

      <div className="improvements-grid">
        {improvements.map((improvement, idx) => (
          <div
            key={idx}
            className={`improvement-card ${
              selectedImprovements.includes(idx) ? 'selected' : ''
            }`}
            onClick={() => toggleImprovement(idx)}
          >
            <div className="improvement-header">
              <div className="checkbox">
                {selectedImprovements.includes(idx) && <CheckCircle2 size={20} />}
              </div>
              <div className="improvement-title">
                <h3>{improvement.improvement}</h3>
                <span className="section-badge">{improvement.section}</span>
              </div>
              <div className="impact-badge">+{improvement.impactScoreIncrease}pts</div>
            </div>

            <div className="improvement-content">
              <p className="improvement-why">{improvement.why}</p>

              {improvement.keywordsAdded && improvement.keywordsAdded.length > 0 && (
                <div className="keywords">
                  <strong>Keywords to add:</strong>
                  <div className="keyword-list">
                    {improvement.keywordsAdded.map((kw, i) => (
                      <span key={i} className="kw">{kw}</span>
                    ))}
                  </div>
                </div>
              )}

              {improvement.bulletPointSuggestion && (
                <div className="bullet-point">
                  <strong>Suggested bullet point:</strong>
                  <p>"{improvement.bulletPointSuggestion}"</p>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default ImprovementsPreview;
