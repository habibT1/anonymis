/** Découpe un texte en morceaux « texte normal » / « jeton » pour les surligner dans la Vue IA. */
export interface Segment {
  text: string;
  token?: string; // ex. PERSONNE
  family?: TokenFamily;
}

export type TokenFamily = 'person' | 'contact' | 'id' | 'secret' | 'place' | 'business';

const TOKEN = /\[([A-Z_]+?)_(\d+)\]|•••• \(([A-Z_]+?)_(\d+)\)/g;

const FAMILIES: Record<string, TokenFamily> = {
  PERSONNE: 'person',
  ORGANISATION: 'business',
  EMAIL: 'contact',
  TELEPHONE: 'contact',
  ADRESSE: 'place',
  CODE_POSTAL: 'place',
  LIEU: 'place',
  NAS: 'id',
  NIR: 'id',
  IBAN: 'id',
  CARTE_BANCAIRE: 'id',
  DATE_NAISSANCE: 'id',
  CLE_API: 'secret',
  CLE_PRIVEE: 'secret',
  JETON: 'secret',
  CONNEXION: 'secret',
  SECRET: 'secret',
  IP: 'secret',
};

export function familyOf(type: string): TokenFamily {
  return FAMILIES[type] ?? 'business';
}

export function segment(text: string | null | undefined): Segment[] {
  if (!text) return [];
  const out: Segment[] = [];
  let last = 0;
  for (const m of text.matchAll(TOKEN)) {
    const start = m.index ?? 0;
    if (start > last) out.push({ text: text.slice(last, start) });
    const type = m[1] ?? m[3];
    out.push({ text: m[0], token: type, family: familyOf(type) });
    last = start + m[0].length;
  }
  if (last < text.length) out.push({ text: text.slice(last) });
  return out;
}

/** Les petits modèles répondent souvent en Markdown malgré la consigne : on l'allège pour l'affichage. */
export function plainText(text: string | null | undefined): string {
  if (!text) return '';
  return text
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^(\s*)[*-]\s+/gm, '$1• ');
}
