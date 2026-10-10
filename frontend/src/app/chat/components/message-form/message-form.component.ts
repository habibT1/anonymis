import { Component, output, ViewChild, ElementRef } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatIconModule }       from '@angular/material/icon';
import { CommonModule }        from '@angular/common';
import { ChatImageData }       from '../../model/message.model';
import { LanguageService }     from 'src/app/shared/services/language.service';

@Component({
  selector: 'app-message-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    CommonModule,
    MatIconModule,
  ],
  templateUrl: './message-form.component.html',
  styleUrls: ['./message-form.component.scss'],
})
export class MessageFormComponent {

  publish = output<{ text: string; imageData: ChatImageData | null }>();

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  messageForm: FormGroup = this.fb.group({
    msg: [''],
  });


  private selectedFile: File | null = null;
  previewUrl: string | null = null;

  constructor(private fb: FormBuilder, protected i18n: LanguageService) {}
  get hasImage(): boolean {
    return this.selectedFile !== null;
  }

  private formIsValid(): boolean {
    const raw = this.messageForm.get('msg')?.value ?? '';
    const cleaned = (raw as string).trim();
    const hasText = cleaned.length > 0;
    const hasImg  = this.selectedFile !== null;
    return hasText || hasImg;
  }

  fileChanged(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;

    this.selectedFile = file;

    if (file) {
      const reader = new FileReader();
      reader.onload = () => {
        this.previewUrl = reader.result as string;
      };
      reader.readAsDataURL(file);
    } else {
      this.previewUrl = null;
    }
  }

  clearImage() {
    this.selectedFile = null;
    this.previewUrl = null;
    // Reset the file input element so it can detect changes when selecting the same file again
    if (this.fileInput) {
      this.fileInput.nativeElement.value = '';
    }
  }

  // convertit un File en base64
  private readFileAsBase64(f: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = err => reject(err);
      reader.onload = () => {
        const full = reader.result as string;
        // full ressemble à "data:image/png;base64,AAAA..."
        const base64Part = full.split(',')[1] ?? full;
        resolve(base64Part);
      };
      reader.readAsDataURL(f);
    });
  }


  async onSubmit() {
    if (!this.formIsValid()) {
      this.messageForm.markAllAsTouched();
      return;
    }

    const raw = this.messageForm.get('msg')?.value ?? '';
    const cleanedText = (raw as string).trim();
    let imageData: ChatImageData | null = null;

    if (this.selectedFile) {
      const base64 = await this.readFileAsBase64(this.selectedFile);
      const mime = this.selectedFile.type;
      const ext = mime.split('/')[1] ?? 'png';

      imageData = {
        data: base64,
        type: ext,
      };
    }

    this.publish.emit({
      text: cleanedText,
      imageData: imageData,
    });

    this.messageForm.reset();
    this.clearImage();
  }
}