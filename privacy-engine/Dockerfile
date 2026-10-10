FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
 && python -m spacy download fr_core_news_md
COPY app.py ./
COPY engine ./engine
EXPOSE 8090
# 0.0.0.0 : uniquement sur le réseau Docker interne (le port n'est pas publié sur la machine).
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8090"]
