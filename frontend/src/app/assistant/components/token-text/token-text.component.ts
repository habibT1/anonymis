import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { plainText, segment } from '../../utils/tokens.util';

/** Affiche un texte en surlignant les jetons ([PERSONNE_1], •••• (CLE_API_1)) par famille. */
@Component({
  selector: 'app-token-text',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@for (s of segments(); track $index) {@if (s.token) {<mark class="tok tok--{{ s.family }}" [attr.title]="s.token">{{ s.text }}</mark>} @else {<span>{{ s.text }}</span>}}`,
  styles: `
    :host { white-space: pre-wrap; word-break: break-word; }
    .tok {
      font-weight: 600;
      padding: 0 3px;
      border-radius: 3px;
      border: 1px solid currentColor;
      background: color-mix(in srgb, currentColor 10%, transparent);
    }
    .tok--person { color: #2b59ff; }
    .tok--contact { color: #0f8a6a; }
    .tok--id { color: #b25e00; }
    .tok--secret { color: #c4213b; }
    .tok--place { color: #7a3fc4; }
    .tok--business { color: #5c5c62; }
  `,
})
export class TokenTextComponent {
  text = input<string | null | undefined>('');
  /** true : allège le Markdown (réponse affichée). false : texte brut (Vue IA). */
  plain = input(false);
  segments = computed(() => segment(this.plain() ? plainText(this.text()) : this.text()));
}
