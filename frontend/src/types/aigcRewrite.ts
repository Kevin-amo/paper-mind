export interface AigcQualityScore {
  directness: number;
  rhythm: number;
  academicTone: number;
  informationDensity: number;
  meaningPreservation: number;
  overall: number;
}

export interface AigcRiskPattern {
  type: string;
  evidence: string;
  suggestion: string;
}

export interface AigcRewriteResponse {
  riskPatterns: AigcRiskPattern[];
  rewrittenText: string;
  changeNotes: string[];
  warnings: string[];
  qualityScore: AigcQualityScore;
}

export type AigcRewriteStrength = 'light' | 'standard' | 'strong';

export interface AigcRewriteRequest {
  paragraph: string;
  discipline?: string;
  rewriteStrength?: AigcRewriteStrength;
  keepTerms?: string[];
  extraRequirements?: string;
}
